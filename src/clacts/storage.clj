(ns clacts.storage
	(:use [clojure.java.jdbc :only [create-table 
									with-connection 
									insert-record 
									with-query-results]]
		  [clojure.java.io]
		  [datomic.api :only [q db] :as d]))

(defprotocol StorageMediumP
	(setup [this])
	(put-fact [this author via fact])
	(find-by-author [this author]))

;;============ SQLite 

(def sqlite-db
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
  (with-connection sqlite-db
    (with-query-results r q)))


(defn setup-db 
  "Setup procedure. DDL the facts table."
  []
	(with-connection sqlite-db
		(create-table :facts
			[:date :text]
			[:author :text]
			[:via :text]
			[:fact :text]))
  (println "Database created with table facts."))


(defrecord SQLiteStorage
	[dbfilechk setupf] 
	StorageMediumP

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
		(with-connection sqlite-db
    		(insert-record :facts
      			{:date (str (System/currentTimeMillis))
       			 :author author
       			 :via via
		         :fact fact})))
	
	;; find fact by author. * means all authors
	(find-by-author [_ author]
		(println "finding " author)
		(let [q "select date, author, via, fact from facts"
			  listq #(with-connection sqlite-db
			  			(with-query-results rs %
			  				(into [] rs)))]
      		(or 
      			(and (= "*" author) (listq [q]))
      			(listq [(str q " where author = ?") author])))))


; =============== Datomic

(defonce uri "datomic:free://localhost:4334/clact")
(defonce datomic-schema "datomic/clact-schema.dtm")

(defn load-schema [s conn]
	(let [s-tx (read-string (slurp s))]
		@(d/transact conn s-tx)))

(defrecord DatomicStorage
	[schema transactor-config conn]
	StorageMediumP

	(setup [_] 
		(load-schema schema conn))
	
	(put-fact [_ author via fact]
		@(d/transact conn [{:clact/via via
					   		:clact/author author
					   		:clact/fact fact 
					   		:db/id #db/id[:db.part/user]}]))

	(find-by-author [_ author]
		(let [db (db conn)
			  res (q '[:find ?e :in $ ?a :where [?e :clact/author ?a]] db author)
			  extract (fn [e] {:author (:clact/author e)
			  				   :via (:clact/via e)
			  				   :date nil
			  				   :fact (:clact/fact e)})
			  as-e #(d/entity db (first %))]
			  (map #(extract (as-e %)) res))))

;;Auxiliary function to generation a connection before the Storage itself

(defn- make-datomic [schema transactor-config]
	(d/create-database uri)
	(DatomicStorage. schema transactor-config (d/connect uri)))

; =============== The rest

(defn make-storage [s]
	(condp = s
		"sqlite" (SQLiteStorage. db-check-file setup-db)
		"datomic" (make-datomic datomic-schema nil)
		nil))

