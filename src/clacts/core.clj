(ns clacts.core
  (:use [clojure.java.jdbc :only [create-table with-connection insert-record with-query-results]]
        [lamina.core] 
        [aleph.tcp] 
        [gloss.core]
        [gloss.io]
        [clojure.java.io]
        [clojure.tools.cli])
  (:require [clacts.prot :as prt])
   (:gen-class))

(defonce broad-put (channel))

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
  [dbfile setupp]
    (let [f (clojure.java.io/file dbfile)]
    (if (.exists f)
          (.setLastModified f (System/currentTimeMillis))
      	(do 
          (println "Setting up db for the first time")
          (.mkdir (file (.getParent f)))
          (spit dbfile "")
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
  [[author via fact] ch]
  (with-connection db
    (insert-record :facts
      {:date (str (System/currentTimeMillis))
       :author author
       :via via
       :fact fact}))
  (enqueue broad-put   ["REP" 
                       (format "####Fact recorded by %s!! By %s: %s." 
                        via author fact)]))


(defn listq 
  "Function that executes a query against facts table.
  Presumes that the query will always select all columns."
  [q ch repmess]
  (with-connection db
    (with-query-results rs q
      (if (not rs)
          (enqueue ch ["REP" repmess])
          (doseq [r rs]
            (enqueue ch ["LSR" (str (:date r)) 
                               (str (:author r)) 
                               (str (:via r))
                               (str (:fact r))]))))))

(defn list-facts 
  "List the facts for a given author. * means all authors."
  [author ch]
    (let [q "select date, author, via, fact from facts"]
      (if (= "*" author)
        (listq [q] ch "Nothing recorded so far. Observer your friends and PUT a lot!!!")
        (listq [(str q " where author = ?") author] 
                ch 
                (format "Nothing found for \"%s\". Try someone else" author)))))
  

(defonce err-msgs ["Wrong commmand! Try again." 
                   "Are you sure you typed right? o.0"
                   "Ask the developer how to use me ;)"])

(defn handle-err 
  "Handle unknown commands responding with random error messages."
  [ch ci]
  (println "Handling err for " ci)
  (enqueue ch ["REP" (rand-nth err-msgs)]))

(defn handler 
  "TCP Handler. Decodes the issued command and calls the appropriate
  function to excetion some action."
  [ch ci]
  (siphon broad-put ch)
  (receive-all ch
    (fn [cmd]
      (println "Processing command: " cmd "From " ci)
      (condp = (first cmd)
        "PUT" (put-fact (rest cmd) ch)
        "LSA" (list-facts (second cmd) ch)
        (handle-err ch ci)))))

(defn -main
  [& args]  
  (let [[opts _ ban] (cli args 
                        ["-p" "--port" "Port to listen to connections"
                         :default 10200 :parse-fn #(Integer/parseInt %)]
                        ["-h" "--help" "Show this help" :default false :flag true])]
    (when (:help opts)
      (println ban)
      (System/exit 0))
    (try 
      (setup "db/database.check" setup-db)
      (warmup warmup-q)
      (println (format "Ready to get facts! Go for it on PORT %s." (:port opts)))
      (start-tcp-server handler {:port (:port opts), :frame prt/CMDS})
      (catch Exception e (.printStackTrace e)))))
