(ns clacts.prot
	(:use [gloss.core]
		  [gloss.io]))

(def p (string :utf-8 :delimiters " "))
(def lp (string :utf-8 :delimiters ["\r\n"]))

(defcodec PUTC ["PUT" p p lp])
(defcodec LSAC ["LSA" lp])
(defcodec REPC ["REP" lp])
(defcodec LSRC ["LSR" (string :utf-8 :suffix " ") 
	                  (string :utf-8 :suffix " ") 
	                  (string :utf-8 :suffix " ") 
	                  (string :utf-8 :suffix "\r\n")])

(defcodec ERRC (string :utf-8))
(defcodec LSCC ["LSC" lp])

(defcodec CMDS 
  (header 
    p
    (fn [h] (condp = h
    	"PUT" PUTC 
    	"LSA" LSAC 
    	"REP" REPC
    	"LSR" LSRC
        "LSC" LSCC
    	ERRC))
    (fn [b] (first b))))