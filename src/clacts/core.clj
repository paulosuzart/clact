(ns clacts.core
  (:use [clacts.storage]
        [lamina.core] 
        [aleph.tcp] 
        [gloss.core]
        [gloss.io]
        [clojure.java.io]
        [clojure.tools.cli])
  (:require [clacts.prot :as prt])
  (:gen-class))

(defonce broad-put (channel))


(defonce err-msgs ["Wrong commmand! Try again." 
                   "Are you sure you typed right? o.0"
                   "Ask the developer how to use me ;)"])

(defn put 
  "Inserts the fact into db according to proto/PUTC.
  Takes the decoded data end the channel to respond."
  [[author via fact] ch storage]
  (put-fact storage author via fact)
  (enqueue broad-put ["REP" 
                      (format "####Fact recorded by %s!! By %s: %s." 
                        via author fact)]))

(defn lsa
  "List the facts for a given author. * means all authors."
  [[author] ch storage]
    (if-let [res (find-by-author storage author)]
      (doseq [r res]
        (enqueue ch ["LSR" (str (:date r)) 
                           (str (:author r)) 
                           (str (:via r))
                           (str (:fact r))]))
      (enqueue ch ["REP" "Nothing found. Try again!"])))

(defn lsc
  "List facts by its content."
  [[content] ch storage]
  (if-let [res (find-by-content storage content)]
    (doseq [r res]
      (enqueue ch ["LSR" (str (:date r))
                         (str (:author r))
                         (str (:via r))
                         (str (:fact r))]))
    (enqueue ch ["REP" "Nothing found. Try again!"])))
  

(defn handle-err 
  "Handle unknown commands responding with random error messages."
  [ch ci]
  (println "Handling err for " ci)
  (enqueue ch ["REP" (rand-nth err-msgs)]))

(defn handler 
  "TCP Handler. Decodes the issued command and calls the appropriate
  function to excetion some action."
  [storage]
    (fn 
      [ch ci]
        (siphon broad-put ch)
        (receive-all ch
          (fn [cmd]
            (println "Processing command: " cmd "From " ci)
            (letfn [(handle [f] (f (rest cmd) ch storage))]
              (condp = (first cmd)
              "PUT" (handle put)
              "LSA" (handle lsa)
              "LSC" (handle lsc)
              (handle-err ch ci)))))))

(defn -main
  [& args]  
  (let [[opts _ ban] (cli args 
                        ["-p" "--port" "Port to listen to connections"
                              :default 10200 :parse-fn #(Integer/parseInt %)]
                        ["-s" "--storage" "Type of storage. Use sqlite or datomic" 
                              :default "sqlite"]
                        ["-t" "--transactor" "Transactor server:port. For datomic only."
                              :default "localhost:4334"]
                        ["-h" "--help" "Show this help" :default false :flag true])]
    (when (:help opts)
      (println ban)
      (System/exit 0))
    (try
      (let [storage (make-storage opts)]
        (setup storage)
        (start-tcp-server (handler storage) {:port (:port opts), :frame prt/CMDS})
        (println (format "Ready to get facts! Go for it on PORT %s." (:port opts)))
        (println (format "Persistence storage is %s." (:storage opts))))
    (catch Exception e 
      (do (.printStackTrace e)
        (System/exit 0))))))
