(ns pallet.crate.resolv
  (:require
   [pallet.action :as action]
   [pallet.script.lib :as lib]
   [pallet.stevedore :as stevedore]
   [pallet.template :as template]
   [pallet.utils :as utils]
   [clojure.string :as string]
   [clojure.tools.logging :as logging]))

(defn- write-key-value [key value]
  (str (name key) " " (utils/as-string value) \newline))

(defn- write-option [[key value]]
  (str (name key)
       (if (and value (not (instance? Boolean value)))
         (str ":" (utils/as-string value)))))

(defn- write-options [options]
  (write-key-value "options" (string/join " " (map write-option options))))

(defn write [domainname nameservers searches sortlist options]
  (str (write-key-value
        "domain" (or domainname (str (stevedore/script (~lib/dnsdomainname)))))
       (string/join (map (partial write-key-value "nameserver") nameservers))
       (when (first searches)
         (write-key-value "search" (string/join " " searches)))
       (when (first sortlist)
         (write-key-value "sortlist" (string/join " " sortlist)))
       (when (first options)
         (write-options options))))

(template/deftemplate resolv-templates
  [domainname nameservers searches sortlist options]
  {{:path "/etc/resolv.conf" :owner "root" :mode "0644"}
   (write domainname nameservers searches sortlist options)})

(defn- ensure-vector [arg]
  (if (vector? arg)
    arg
    (if arg [arg] [])))

(defn- merge-resolve-spec [m1 m2]
  (let [[d1 n1 s1 r1 opt1] m1
        [d2 n2] m2
        opt2 (apply hash-map (drop 2 m2))
        r [(or d1 d2)
           (concat (ensure-vector n1) (ensure-vector n2))
           (concat s1 (ensure-vector (:search opt2)))
           (concat r1 (ensure-vector (:sortlist opt2)))
           (merge opt1 (dissoc (dissoc opt2 :search) :sortlist))]]
    (if-not (or (nil? d1) (nil? d2) (= d1 d2))
      (logging/warn "Trying to set domain name to two distinct values"))
    r))

(action/def-aggregated-action resolv
  "Resolv configuration. Generates a resolv.conf file.
options are:

:search    search order
:sortlist  sortlist

or one of the resolv.conf options"
  {:arglists '([session domainname nameservers & {:keys [search sortlist]}])}
  [session args]
  (logging/trace "apply-resolv")
  (template/apply-templates
   resolv-templates
   (reduce merge-resolve-spec [nil [] [] [] {}] args)))
