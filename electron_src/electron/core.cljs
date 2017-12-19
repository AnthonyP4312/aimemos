(ns electron.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.crypt.base64 :refer [encodeString]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(def electron       (js/require "electron"))
(def app            (.-app electron))
(def browser-window (.-BrowserWindow electron))
(def crash-reporter (.-crashReporter electron))
(def ipc            (.-ipcMain electron))
(def wsClient (.-client (js/require "websocket")))

(def buddy-list (atom nil))
(def dialup-splash (atom nil))
(def login-window (atom nil))
(def chat-windows (atom []))
(def ws (atom nil))

(enable-console-print!)

(defn new-window! [window options]
  (reset! window (browser-window. (clj->js options)))
  (.loadURL @window (str "file://" js/__dirname "/public/index.html"))
  (.on  @window "closed" #(reset! window nil)))

(defn open-chat-window [screen-name]
  (let [chat (browser-window.
              (clj->js {:icon "img/aim.png"
                        :meta {:class "chat-window" :title screen-name}
                        :width 500
                        :height 500
                        :frame false
                        :resizable true
                        :titleBarStyle "hidden"}))]                       
    (.loadURL chat (str "file://" js/__dirname "/public/index.html"))
    (swap! chat-windows conj chat)))

(defn socket-message
  [message]
  (println message))

(.on ipc "open-buddy-list" 
  (fn [event, user]
    (println user)
    (new-window! buddy-list (clj->js {:icon "img/aim.png"
                                      :meta {:class "buddy-list"
                                             :username user}
                                      :frame false
                                      :titleBarStyle "hidden"
                                      :width 300
                                      :height 700}))
    (set! (.-returnValue event) "bitch")))

(.on ipc "open-login-window" 
     
  (fn [event, meta]
    (new-window! login-window (clj->js {:meta {:class "login-window"}
                                        :frame false
                                        :titleBarStyle "hidden"
                                        :width 300
                                        :height 500
                                        :icon "img/aim.png"
                                        :resizable true}))
    (set! (.-returnValue event) "bitch")))

(.on ipc "open-dialup-splash" 
  (fn [event, meta]
    (new-window! dialup-splash (clj->js {:meta meta
                                         :frame false
                                         :titleBarStyle "hidden"
                                         :width 1000
                                         :icon "img/aim.png"
                                         :height 475}))
    (set! (.-returnValue event) "bitch")))

(.on ipc "open-chat" 
  (fn [event, info]
    (open-chat-window (.-username info))
    (set! (.-returnValue event) "bitch")))
                 
(.on ipc "create-socket"
     (fn [event, info]
       (println "Creating a socket!")
       (let [chan (wsClient.)]
         (.connect chan "ws://10.0.0.171:3000/ws" nil nil 
                   (clj->js {:Authorization (str "Basic " (encodeString info))}))
         (.on chan "connect" (fn [conn]
                               (println "connected!")
                               (.on conn "message" socket-message)
                               (.on conn "frame" (partial println "thisaframe"))
                               (.send (.-sender event) "login-success" "nice")       
                               (reset! ws conn)))
         (.on chan "connectFailed" #(.send (.-sender event) "login-failure" "rip")))
       (println "registered all the listeners")))

(.on ipc "socket-action"
     (fn [event, info]
       (print "Im doin the socket thing with " info)
       (.sendUTF @ws info)))

; CrashReporter can just be omitted
(.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
                                (.quit app)))                        

(.on app "ready" #(new-window! login-window {:meta {:class "dialup-splash"}
                                              :frame false
                                              :titleBarStyle "hidden"
                                              :resizable false
                                              :width 910
                                              :icon "img/aim.png"
                                              :height 432}))
