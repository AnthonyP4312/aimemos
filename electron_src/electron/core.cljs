(ns electron.core)

(def electron       (js/require "electron"))
(def app            (.-app electron))
(def browser-window (.-BrowserWindow electron))
(def crash-reporter (.-crashReporter electron))
(def ipc            (.-ipcMain electron))

(def buddy-list (atom nil))
(def dialup-splash (atom nil))
(def login-window (atom nil))
(def chat-windows (atom []))


(defn new-window! [window class]
  (.log js/console window class)
  (reset! window (browser-window.
                          (clj->js {:meta {:class class}
                                    :frame false
                                    :titleBarStyle "hidden"
                                    :width 1200
                                    :height 700})))
  (.loadURL @window (str "file://" js/__dirname "/public/index.html"))
  (.on  @window "closed" #(reset! window nil)))

(defn open-chat-window [screen-name]
  (let [chat (browser-window.
              (clj->js {:meta {:class "chat" :title screen-name}
                        :width 500
                        :height 500
                        :frame false
                        :resizable true
                        :titleBarStyle "hidden"}))]
    (.log js/console chat.webContents)                        
    (.loadURL chat (str "file://" js/__dirname "/public/chat.html"))
    (swap! chat-windows conj chat)))

(.on ipc "open-chat" (fn [event, info]
                        (.log js/console info)
                        (open-chat-window (.-username info))
                        (set! (.-returnValue event) "bitch")))
                        

; CrashReporter can just be omitted
(.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
                                (.quit app)))                        
(.on app "ready" #(new-window! buddy-list "buddy-list"))
