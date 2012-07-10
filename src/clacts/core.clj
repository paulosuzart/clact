(ns clacts.core
  (:use [clojure.java.jdbc :only [create-table with-connection insert-record with-query-results]]
        [lamina.core] 
        [aleph.tcp] 
        [gloss.core]
        [gloss.io])
  (:require [clacts.prot :as prt])
   (:gen-class))

(def db
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "db/database.db"})

;; Simple query to warm the database
(defonce warmup-q ["select count (*) from facts"])

(defn warmup 
  "Access SQLite at sturt up, so users don't have to wait
  for connecting to ig."
  [q]
  (with-connection db
    (with-query-results r q)))

(defn setup
  "Temp way to check if the app was already set.
  Takes f-name for the control file and setupp as the
  function that will set up the database if it is not set."
  [f-name setupp]
    (let [f (clojure.java.io/file f-name)]
    (if (.exists f)
          (.setLastModified f (System/currentTimeMillis))
      	(do (println "Setting up db for the first time")
          (spit f-name "")
          (setupp)))))

(defn setup-db 
  "Setup procedure. DDL the facts table."
  []
	(with-connection db
		(create-table :facts
			[:date :text]
			[:author :text]
			[:via :text]
			[:fact :text]))
  (println "Database created with table facts."))

(defn put-fact 
  "Inserts the fact into db according to proto/PUTC.
  Takes the decoded data end the channel to respond."
  [data ch]
  (with-connection db
    (insert-record :facts
      {:date (str (System/currentTimeMillis))
       :author (first data)
       :via (second data)
       :fact (last data)}))
  (enqueue ch (encode prt/CMDS ["REP" "Fact recorded!! Have fun with it."])))


(defn listq 
  "Function that executes a query against facts table.
  Presumes that the query will always select all columns."
  [q ch]
  (with-connection db
    (with-query-results rs q 
      (doseq [r rs]
        (enqueue ch (encode prt/CMDS ["LSR" (str (:date r)) 
                                            (str (:author r)) 
                                            (str (:via r))
                                            (str (:fact r))]))))))

(defn list-facts 
  "List the facts for a given author. * means all authors."
  [author ch]
    (let [q "select date, author, via, fact from facts"]
      (if (= "*" author)
        (listq [q] ch)
        (listq [(str q " where author = ?") author] ch))))
  

(defonce err-msgs ["Wrong commmand! Try again." 
                   "Are you sure you typed right? o.0"
                   "Ask the developer how to use me ;)"])

(defn handle-err 
  "Handle unknown commands responding with random error messages."
  [ch ci]
  (println "Handling err for " ci)
  (enqueue ch (encode prt/CMDS ["REP" (rand-nth err-msgs)])))

(defn handler 
  "TCP Handler. Decodes the issued command and calls the appropriate
  function to excetion some action."
  [ch ci]
  (receive-all ch
    (fn [b]
      (println b)
      (let [deced (decode prt/CMDS b)]
        (println "Processing command: " deced)
        (condp = (first deced)
          "PUT" (put-fact (rest deced) ch)
          "LSA" (list-facts (second deced) ch)
          (handle-err ch ci))))))

(defn -main
  [& args]  
  (try 
    (setup "db/database.check" setup-db)
    (warmup warmup-q)
    (println "Ready to get facts! Go for it.")
    (start-tcp-server handler {:port 10000})
    (catch Exception e (.printStackTrace e))))
