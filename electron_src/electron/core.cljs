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
(def wsClient (js/require "ws"))

(def buddy-list (atom nil))
(def dialup-splash (atom nil))
(def login-window (atom nil))
(def chat-windows (atom {}))
(def ws (atom nil))
(def this-user (atom nil))

(enable-console-print!)

(defn new-window! [window options]
  (reset! window (browser-window. (clj->js options)))
  (.loadURL @window (str "file://" js/__dirname "/public/index.html"))
  (.on  @window "closed" #(reset! window nil)))

(defn open-chat-window [screen-name]
  (let [chat (browser-window.
              (clj->js {:icon (str js/__dirname "/public/img/aim.png")
                        :meta {:class "chat-window"
                               :title screen-name
                               :username @this-user}
                        :width 500
                        :height 500
                        :frame false
                        :resizable true
                        :titleBarStyle "hidden"}))]
    (.loadURL chat (str "file://" js/__dirname "/public/index.html"))
    (swap! chat-windows assoc (keyword screen-name) chat)))

(defn send-chat-message
  [user message]
  (let [window (@chat-windows (keyword user))]
    (println "Sending the message back to render" user message window)
    (.send (.-webContents window) (str "chat-" user) message)))

(defn socket-message
  [m]
  (let [message (js->clj (.parse js/JSON m) :keywordize-keys true)]
    (if (vector? message)
      (do ;; Updated buddies
        (when @buddy-list
          (.send ipc "buddy-update" message)))
      (do ;; A Message
        (println "clj message" message)
        (println (keyword (:from message)))
        (println @chat-windows)
        (if ((keyword (:from message)) @chat-windows) ;; is this chat window open?
          (send-chat-message (:from message) (:message message))
          (do
            (open-chat-window (:from message))
            (js/setTimeout
             #(send-chat-message (:from message) (:message message)) 500)))))))

(.on ipc "open-buddy-list"
  (fn [event, user]
    (reset! this-user user)
    (new-window! buddy-list (clj->js {:icon (str js/__dirname "/public/img/aim.png")
                                      :meta {:class "buddy-list"
                                             :username user}
                                      :frame false
                                      :titleBarStyle "hidden"
                                      :width 300
                                      :height 700}))
    (set! (.-returnValue event) "buddy list opened")))

(.on ipc "open-login-window"

  (fn [event, meta]
    (new-window! login-window (clj->js {:meta {:class "login-window"}
                                        :frame false
                                        :titleBarStyle "hidden"
                                        :width 300
                                        :height 500
                                        :icon (str js/__dirname "/public/img/aim.png")
                                        :resizable true}))
    (set! (.-returnValue event) "bitch")))

(.on ipc "open-dialup-splash"
  (fn [event, meta]
    (new-window! dialup-splash (clj->js {:meta meta
                                         :frame false
                                         :titleBarStyle "hidden"
                                         :width 1000
                                         :icon (str js/__dirname "/public/img/aim.png")
                                         :height 475}))
    (set! (.-returnValue event) "bitch")))

(.on ipc "open-chat"
  (fn [event, info]
    (open-chat-window (.-username info))
    (set! (.-returnValue event) "oh boy")))

(.on ipc "create-socket"
     (fn [event, info]
       (println "Creating a socket!")
       (let [chan (wsClient.
                   "ws://69.164.212.77:80/ws" nil
                   (clj->js {:perMessageDeflate false
                             :rejectUnauthorized false
                             :headers
                             {:Authorization (str "Basic " (encodeString info))}}))]
         (.on chan "open" (fn []
                            (println "connected!")
                            (js/setInterval #(.ping chan) 5000)
                            (.send (.-sender event) "login-success" "nice")))
         (.on chan "message" socket-message)
         (.on chan "frame" (partial println "thisaframe"))
         (reset! ws chan)
         (.on chan "unexpected-response"
              #(.send (.-sender event) "login-failure" "rip")))))

(.on ipc "socket-action"
     (fn [event, info]
       (print "Im doin the socket thing with " info)
       (.send @ws info)))

; CrashReporter can just be omitted
(.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
                                (.quit app)))

(.on app "ready" #(new-window! login-window {:meta {:class "dialup-splash"}
                                             :frame false
                                             :titleBarStyle "hidden"
                                             :resizable false
                                             :width 910
                                             :icon (str js/__dirname "/public/img/aim.png")
                                             :height 432}))
