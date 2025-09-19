# VRipper!

This is my spin for a cross-platform gallery ripper for [vipergirls.to](https://vipergirls.to)

![GitHub Image](/image.png)

## Donation
To support this project, you can make a donation to its current maintainer

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://buymeacoffee.com/devclaw)

Or with Cryptocurrency

ETH: 0xDdac82B16dC5E3D742fc915ffF583D8548A301cA

BTC: bc1qcqudnkrndwyadsjwrxww42svkf8trnzx3c8vlr

## Requirements
Direct access to `vipergirls.to` domain.

## Installing VRipper

<img src="https://github.com/stashapp/stash/raw/develop/docs/readme_assets/windows_logo.svg" width="100%" height="75"> Windows | <img src="https://github.com/stashapp/stash/raw/develop/docs/readme_assets/mac_logo.svg" width="100%" height="75"> macOS (Intel) | <img src="https://github.com/stashapp/stash/raw/develop/docs/readme_assets/mac_logo.svg" width="100%" height="75"> macOS (Apple silicon) | <img src="https://github.com/stashapp/stash/raw/develop/docs/readme_assets/linux_logo.svg" width="100%" height="75"> Linux  | <img src="https://images.vexels.com/media/users/3/166401/isolated/preview/b82aa7ac3f736dd78570dd3fa3fa9e24-java-programming-language-icon-by-vexels.png" width="100%" height="75"> Java
:---:|:---:|:---:|:---:|:---:
[Installer (EXE)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-windows-installer-6.5.7.exe) <br /> [Installer (MSI)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-windows-installer-6.5.7.msi) <br /> [Portable (ZIP)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-windows-portable-6.5.7.zip) | [Installer (DMG)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-macos-6.5.7.x86_64.dmg) <br /> [Installer (PKG)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-macos-6.5.7.x86_64.pkg) <br /> [Portable (ZIP)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-macos-portable-6.5.7.x86_64.zip) | [Installer (DMG)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-macos-6.5.7.arm64.dmg) <br /> [Installer (PKG)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-macos-6.5.7.arm64.pkg) <br /> [Portable (ZIP)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-macos-portable-6.5.7.arm64.zip)  | [Linux (amd64) (DEB)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-linux-6.5.3_amd64.deb) <br /> [Linux (x86_64) (RPM)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-linux-6.5.7.x86_64.rpm) <br /> [Portable (ZIP)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-linux-portable-6.5.7.zip) | [Java GUI (noarch)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-noarch-gui-6.5.7.jar) <br /> [Java Web (noarch)](https://github.com/dev-claw/vripper-project/releases/download/6.5.7/vripper-noarch-web-6.5.7.jar)

Source code and previous versions are available on
the [Releases page](https://github.com/dev-claw/vripper-project/releases).

Application data (application logs, settings and persisted data) is stored in:  
* Windows --> `C:\USERS\<your Windows username>\vripper` 
* Linux and macOS --> `HOME_FOLDER/.config/vripper`


## Important Note About Proxies
The use of proxies within VRipper is worthless, please stop using them **for now**. You will get **403 error** codes.  

**403** is a response code coming from Cloudflare to block VRipper from accessing the site, Cloudflare is doing the job it is supposed to do, which is blocking automated requests from accessing the site. 

If your ISP is blocking access to `vipergirls.to` domain, consider using a VPN or [Cloudflare WARP](https://one.one.one.one/) to bypass the block.

## Supported Image Hosts
The following hosts are supported:
* acidimg.cc  
* imagetwist.com  
* imagezilla.com  
* imgspice.com  
* imagebam.com  
* imgbox.com  
* imx.to  
* pimpandhost.com  
* pixhost.to  
* pixxxels.cc  
* turboimagehost.com  
* postimg.cc  
* imagevenue.com  
* pixroute.to  
* vipr.im  

## Instructions to run from Jar file
You need Java 21+, you can download from https://adoptium.net/

Download the latest jar file from the Release page, open a command prompt and run the jar file using the following command

For the GUI app

    javaw -jar vripper-gui.jar

For the WEB app

    java -jar vripper-web.jar

Application data (application logs, settings and persisted data) is stored in the location where you launched the jar for both GUI and WEB


## How to build

You need JDK 21 and a recent version of maven 3.8.x+

To build, run the following command:

    mvn clean install

Build artifact is located under

    vripper-project\vripper-gui\target\vripper-gui-{{version}}-jar-with-dependencies.jar

Copy the artifact into any other folder and run:

    java -jar vripper-gui-{{version}}-jar-with-dependencies.jar
