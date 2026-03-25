# STSTrainer

A simple trainer for **Slay the Spire** using Java.

---

## 🎮 How to Use (For Users)

1.  Download `STSTrainer.jar` from **[Releases]**.
2.  Move `STSTrainer.jar` into your **Slay the Spire game directory** (e.g., `C:\SteamLibrary\steamapps\common\SlayTheSpire\`).
3.  Right-click **Slay the Spire** in Steam -> **Properties** -> **General** -> **Launch Options**.
4.  Paste the following command:
    ```
    Path To SlayTheSpire\jre\bin\java -javaagent:STSTrainer.jar -jar desktop-1.0.jar %command%
    ```
    > **Note:** Please replace the path `Path To SlayTheSpire` with your actual game installation directory.
5.  Launch the game through Steam.

---

## 🛠️ Development (For Developers)

If you want to modify or recompiling this project, you will need **Javassist**.

* **Dependency**: `javassist.jar`
* **Usage**: This project uses Javassist for bytecode manipulation. Make sure to include `javassist.jar` in your build path or project libraries.

---

## 📄 License

[MIT License](LICENSE)
