# AirChat — Phase 1 (Bluetooth + Wi-Fi Direct + Group Chat + Images)

## इस फ़ोल्डर में क्या है

यह एक पूरा Android Studio project है। इसमें ये सब काम कर रहा है:

- Bluetooth se device dhoondhna aur connect karna
- Wi-Fi Direct se device dhoondhna aur group banana
- Text messages bhejna/receive karna
- Images bhejna/receive karna (auto-compressed for speed)
- Group chat ka base (Wi-Fi Direct group owner sabko relay karta hai)
- Dark theme (WhatsApp jaisa)

## Setup karne ka tareeka (step by step)

### 1. Android Studio install karein
- Yahan se download karein: https://developer.android.com/studio
- Install karte waqt sab default options rakhein

### 2. Project kholein
- Android Studio open karein
- "Open" pe click karein
- Is `AirChat` folder ko select karein
- Pehli baar kholne pe "Gradle sync" hoga — internet chalu rakhein, 2-5 minute lagenge

### 3. Apna phone connect karein (testing ke liye)
- Phone mein Settings → About Phone → Build Number pe 7 baar tap karein (Developer Mode on hoga)
- Settings → Developer Options → USB Debugging ON karein
- Phone ko USB cable se computer se jodein
- Phone par "Allow USB Debugging?" popup aayega — Allow karein

### 4. App run karein
- Android Studio mein upar green ▶️ (Run) button dabayein
- Apna phone list mein dikhega — select karein
- App phone par install ho jayegi

### 5. Testing (2 phone chahiye)
- Dono phone par yeh app install karein (USB se ek-ek karke, ya APK बनाकर doosre phone mein bhejein)
- Dono mein Bluetooth ON karein
- Dono mein app khol kar "Scan for Devices" dabayein
- Doosre phone ka naam dikhega — tap karke connect karein
- Connect hone ke baad chat khulegi — message type karke bhejein

### 6. APK file banane ke liye (taaki kisi ko bhi bhej sakein, install karne ke liye)
Android Studio mein:
- Build → Build Bundle(s) / APK(s) → Build APK(s)
- Build complete hone ke baad "locate" link pe click karein
- Wahan `app-debug.apk` file milegi — yahi file doosre logon ko bhej sakte hain

## Mobile se APK banane ka tareeka (Laptop ke bina)

Agar aapke paas computer nahi hai, toh GitHub ke through mobile se hi APK ban sakti hai.

### Step 1: Files upload karein
1. GitHub par apni `AirChat` repository kholein
2. **"Add file" → "Upload files"** par tap karein
3. Is poore folder ke andar ki **saari files aur folders** select karke upload karein
   (`.github` folder bhi zaroor upload karein — usी mein build ka instruction hai)
4. Neeche **"Commit changes"** par tap karein

### Step 2: Build apne aap shuru hogi
1. Upload hote hi GitHub apne aap APK banana shuru kar dega
2. Repository ke upar **"Actions"** tab par jaayein
3. Wahan ek build chal rahi dikhegi (yellow dot = chal rahi hai, green tick = ho gaya, 2-5 minute lagte hain)

### Step 3: APK download karein
1. Jab build green tick ho jaaye, usi build entry par tap karein
2. Neeche **"Artifacts"** section mein **"AirChat-APK"** dikhega
3. Usi par tap karke download karein — ek ZIP file aayegi
4. Us ZIP ko extract karein (mobile file manager se) — andar `app-debug.apk` milegi

### Step 4: Install karein
1. `app-debug.apk` par tap karein
2. Agar "Install blocked" jaisa message aaye, toh Settings mein jaakar "Install from unknown sources" allow karein (phone khud raasta dikha dega)
3. Install ho jaayegi — App drawer mein **AirChat** dikhega



- **Permissions**: App pehli baar khulte hi Bluetooth, Location, aur Wi-Fi permissions maangegi — sab "Allow" karna zaroori hai, warna scanning kaam nahi karegi.
- **Emulator pe test mat karein**: Bluetooth/Wi-Fi Direct emulator mein kaam nahi karta — asli phone chahiye, kam se kam 2.
- **Range**: Bluetooth ki range ~10 meter hai, Wi-Fi Direct ki ~50-100 meter.

## Agla Phase

Yeh Phase 1 hai — poora feature set already isi mein hai (jaisa aapne maanga tha). Ab agla kaam hai:
1. Real devices par test karna
2. Jo bugs ya issues aayein unhe fix karna
3. App ko polish karna (animations, better UI details)
4. Play Store ke liye taiyaari (icon finalize, privacy policy, listing)

Jo bhi error ya problem aaye, screenshot ya error message bhej dena — main turant fix karunga.
