(ns electron.core)

(def electron       (js/require "electron"))
(def app            (.-app electron))
(def browser-window (.-BrowserWindow electron))
(def crash-reporter (.-crashReporter electron))
(def ipc            (.-ipcMain electron))

(def main-window (atom nil))
(def chat-windows (atom []))

(defn buddy-list []
  (reset! main-window (browser-window.
                        (clj->js {:meta {}
                                  :frame false
                                  :titleBarStyle "hidden"
                                  :width 1200
                                  :height 700})))
  ; Path is relative to the compiled js file (main.js in our case)
  (.loadURL @main-window (str "file://" js/__dirname "/public/index.html"))
  (.on  @main-window "closed" #(reset! main-window nil)))


(defn open-chat [screen-name]
  (.log js/console screen-name)
  (let [chat (browser-window.
              (clj->js {:meta {:title screen-name}
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
                        (open-chat (.-username info))
                        (set! (.-returnValue event) "bitch")))
                        

; CrashReporter can just be omitted
(.start crash-reporter
        (clj->js
          {:companyName "MyAwesomeCompany"
           :productName "MyAwesomeApp"
           :submitURL "https://example.com/submit-url"
           :autoSubmit false}))

(.on app "window-all-closed" #(when-not (= js/process.platform "darwin")
                                (.quit app)))
(.on app "ready" buddy-list)
