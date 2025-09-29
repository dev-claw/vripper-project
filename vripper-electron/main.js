require('v8-compile-cache');
const {app, BrowserWindow} = require("electron");
const path = require("path");
const url = require("url");
const getPort = require("get-port");
const {spawn} = require("child_process");
const {ipcMain} = require("electron");
const {dialog} = require("electron");
const axios = require('axios');
const windowStateKeeper = require('electron-window-state');

// non null value when it is an AppImage
const appImageDir = process.env.APPDIR;
const appImagePath = process.env.APPIMAGE;

let win;
let vripperServer;
let serverPort;

const maxTerminationAttemps = 5;
let terminationAttemps = 0;
let terminationInteval;
let terminated = false;

process.on("uncaughtException", err => {
    dialog.showErrorBox(err.message, err.stack);
    process.exit(1);
});

createWindow = () => {

    if (process.platform === 'win32') {
        app.setAppUserModelId('me.vripper.vripper-app');
    }

    let icon;
    if (process.platform === 'win32') {
        icon = __dirname + '/icon.ico';
    } else if (process.platform === 'linux') {
        icon = __dirname + '/icon.png';
    }

    const mainWindowState = windowStateKeeper({
        defaultWidth: 1024,
        defaultHeight: 800
    });

    win = new BrowserWindow({
        x: mainWindowState.x,
        y: mainWindowState.y,
        width: mainWindowState.width,
        height: mainWindowState.height,
        minWidth: 800,
        minHeight: 600,
        webPreferences: {
            nodeIntegration: true,
            enableRemoteModule: true
        },
        icon: icon
    });

    mainWindowState.manage(win);
    win.removeMenu();
    win.setMenu(null);
    win.setMenuBarVisibility(false);

    win.loadURL(
        url.format({
            pathname: path.join(__dirname, `vripper-ui/index.html`),
            protocol: "file:",
            slashes: true
        })
    );

    // win.webContents.openDevTools();

    win.on("closed", () => {
        win = null;
    });
}

shutdownServer = () => {
    axios.post('http://localhost:' + serverPort + '/actuator/shutdown', {}, {
        headers: {'content-type': 'application/json'},
    }).then((response) => {
        terminationInteval = setInterval(() => {
            terminationAttemps++;
            if (terminated) {
                console.log('viper server terminated');
                clearInterval(terminationInteval);
                app.quit();
            } else if (terminationAttemps > maxTerminationAttemps) {
                console.log('viper server is not terminated');
                console.log('Proceed to kill');
                vripperServer.kill('SIGKILL');
                clearInterval(terminationInteval);
                app.quit();
            }
        }, 1000);
    }).catch((error) => {
        // Terminate immediately
        console.log(error);
        vripperServer.kill('SIGKILL');
        terminated = true;
        app.quit();
    });
}

const gotTheLock = app.requestSingleInstanceLock();

if (!gotTheLock) {
    app.quit();
} else {
    getPort().then(port => {
        serverPort = port;
        ipcMain.on("get-port", event => {
            event.reply("port", port);
        });

        const appPath = path.join(app.getAppPath(), '../../');
        let javaBinPath, jarPath, baseDir;
        if (appImageDir !== undefined && process.platform === 'linux') {
            javaBinPath = path.join(appImageDir, "java-runtime/bin/java");
            jarPath = path.join(appImageDir, "bin/vripper-server.jar");
            baseDir = path.join(appImagePath, '..');
        } else if (process.platform === 'darwin') {
            javaBinPath = path.join(appPath, "java-runtime/bin/java");
            jarPath = path.join(appPath, "bin/vripper-server.jar");
            baseDir = app.getPath('appData');
        } else if (process.platform === 'win32') {
            javaBinPath = path.join(appPath, "java-runtime/bin/java");
            jarPath = path.join(appPath, "bin/vripper-server.jar");
            baseDir = appPath;
        } else {
            console.error(`Unknown platform ${process.platform}`);
            app.quit();
        }

        vripperServer = spawn(javaBinPath, [
            "-Xms256m",
            "-Dvripper.server.port=" + port,
            "-Dbase.dir=" + baseDir,
            "-Djava.net.preferIPv4Stack=true",
            "-jar",
            jarPath
        ], {
            stdio: 'ignore'
        });
        vripperServer.on('exit', (code, signal) => {
            console.log(`vripper server terminated, code = ${code}, signal = ${signal}`);
            terminated = true;
            app.quit();
        });
    });

    app.on("second-instance", (event, commandLine, workingDirectory) => {
        if (win) {
            if (win.isMinimized()) win.restore();
            win.focus();
        }
    });

    app.on("ready", createWindow);

    app.on("window-all-closed", () => {
        if (process.platform !== "darwin") {
            shutdownServer();
        }
    });

    app.on("will-quit", () => {
        if (process.platform === "darwin") {
            shutdownServer();
        }
    });

    app.on("activate", () => {
        if (win === null) {
            createWindow();
        }
    });
}
