(defproject clacts "0.1.0-SNAPSHOT"
  :description "Clacts: Record your friends facts"
  :url "paulosuzart.guithub.com/blog/2012/07/09/tcp-server-with-clojure-aleph-and-gloss/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[aleph "0.2.1-rc5"]
                 [org.clojure/java.jdbc "0.2.3"]
                 [org.xerial/sqlite-jdbc "3.6.16"]
  				 [org.clojure/clojure "1.3.0"]]
  :main clacts.core)
