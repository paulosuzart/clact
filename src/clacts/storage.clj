(ns clacts.storage
	(:use [clojure.java.jdbc :only [create-table 
									with-connection 
									insert-record 
									with-query-results]]
		  [clojure.java.io]))

(defprotocol PersistentMedium
	(setup [this])
	(put-fact [this author via fact])
	(find-by-author [this author]))


(def db
  {:classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "db/database.db"})

(defonce db-check-file "db/database.check")

;; Simple query to warm the database
(defonce warmup-q ["select count (*) from facts"])

(defn warmup 
  "Access SQLite at sturt up, so users don't have to wait
  for connecting to ig."
  [q]
  (with-connection db
    (with-query-results r q)))


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


(defrecord SQLitePersistence 
	[dbfilechk setupf] 
	PersistentMedium

	;;setup sqlite
	(setup [_]
		(let [f (clojure.java.io/file dbfilechk)]
    		(if (.exists f)
          		(.setLastModified f (System/currentTimeMillis))
      			(do 
          			(println "Setting up db for the first time")
          			(.mkdir (file (.getParent f)))
          			(spit dbfilechk "")
          		(setupf)))))
	
	;; insert a fact into sqlite
	(put-fact [_ author via fact]
		(with-connection db
    		(insert-record :facts
      			{:date (str (System/currentTimeMillis))
       			 :author author
       			 :via via
		         :fact fact})))
	
	;; find fact by author. * means all authors
	(find-by-author [_ author]
		(println "finding " author)
		(let [q "select date, author, via, fact from facts"
			  listq #(with-connection db
			  			(with-query-results rs %
			  				(into [] rs)))]
      		(or 
      			(and (= "*" author) (listq [q]))
      			(listq [(str q " where author = ?") author])))))


(defn make-storage [s]
	(condp = s
		"sqlite" (SQLitePersistence. db-check-file setup-db)
		nil))

