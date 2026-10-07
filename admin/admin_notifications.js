// Sanatan Seva Samiti (SSS) - Push Notification Admin Dispatcher & Firestore Sync Engine
document.addEventListener("DOMContentLoaded", () => {
    initNotificationAdmin();
});

const MAX_TITLE_LEN = 65;
const MAX_BODY_LEN = 240;

const presets = {
    devi: {
        title: "॥ जय माँ भवानी ॥ विशेष दर्शन",
        body: "आज के पावन दिवस पर <b>माँ जगदम्बा</b> के दिव्य दर्शन प्राप्त करें एवं <i>असीम कृपा</i> के भागी बनें।",
        image: "assets/branding/devi.png",
        target: "Home",
        extra: "darshan_devi"
    },
    guru: {
        title: "सद्गुरुदेव जी महाराज का पावन संदेश",
        body: "सेवा ही परमो धर्मः। <span style='color:#ffd866;'>धर्म की रक्षा ही सर्वोपरि संकल्प है।</span> आज का सत्संग विचार पढ़ें।",
        image: "assets/branding/guru.png",
        target: "TrainingLab",
        extra: "satsang_lecture"
    },
    gauseva: {
        title: "सनातन गौ सेवा एवं अन्नदान महाअभियान",
        body: "श्री सनातन सेवा समिति द्वारा <b>विशाल गौ सेवा शिविर</b> का आयोजन। <a href='#'>सहयोग हेतु यहाँ क्लिक करें</a>।",
        image: "assets/branding/sss.png",
        target: "EngineDetails",
        extra: "gau_seva_camp"
    },
    panchang: {
        title: "दैनिक पंचांग एवं शुभ मुहूर्त",
        body: "आज का <b>शुभ मुहूर्त, राहुकाल एवं विशेष चौघड़िया</b> विवरण देखें और अपना दिन मंगलमय बनाएं।",
        image: "assets/branding/app_logo.png",
        target: "Profile",
        extra: "daily_panchang"
    }
};

function initNotificationAdmin() {
    const titleInput = document.getElementById("notifTitle");
    const bodyInput = document.getElementById("notifBody");
    const imageInput = document.getElementById("notifImage");
    const fileInput = document.getElementById("notifImageFile");
    const targetSelect = document.getElementById("targetScreen");
    const extraInput = document.getElementById("extraData");
    const dispatchBtn = document.getElementById("dispatchBtn");

    // Live Listeners with strict limit enforcement
    if (titleInput) titleInput.addEventListener("input", handleTitleInput);
    if (bodyInput) bodyInput.addEventListener("input", handleBodyInput);
    if (imageInput) imageInput.addEventListener("input", updatePreview);
    if (targetSelect) targetSelect.addEventListener("change", updatePreview);
    if (extraInput) extraInput.addEventListener("input", updatePreview);

    // File input attachment reader
    if (fileInput) {
        fileInput.addEventListener("change", (e) => {
            const file = e.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = function(evt) {
                    if (imageInput) {
                        imageInput.value = evt.target.result;
                        updatePreview();
                        showToast("बैनर चित्र सफलतापूर्वक लोड हो गया!");
                    }
                };
                reader.readAsDataURL(file);
            }
        });
    }

    if (dispatchBtn) dispatchBtn.addEventListener("click", handleDispatch);

    // Initial render
    updatePreview();
    renderHistory();

    // Sync from Firestore if connected to Android
    syncFromFirestore();
}

// Strip HTML tags to measure raw text length
function getCleanText(htmlStr) {
    if (!htmlStr) return "";
    const tempDiv = document.createElement("div");
    tempDiv.innerHTML = htmlStr;
    return tempDiv.textContent || tempDiv.innerText || "";
}

// Strict Title Limit (65 Chars)
function handleTitleInput(e) {
    const input = e.target;
    if (input.value.length > MAX_TITLE_LEN) {
        input.value = input.value.substring(0, MAX_TITLE_LEN);
    }
    const currentLen = input.value.length;
    const remaining = MAX_TITLE_LEN - currentLen;
    const counterEl = document.getElementById("titleCounter");
    if (counterEl) {
        counterEl.textContent = `${currentLen}/${MAX_TITLE_LEN} (${remaining} शेष)`;
        if (remaining === 0) {
            counterEl.className = "char-counter limit-reached";
        } else if (remaining <= 10) {
            counterEl.className = "char-counter warning";
        } else {
            counterEl.className = "char-counter";
        }
    }
    updatePreview();
}

// Strict Body Limit (240 Plain-Text Chars)
function handleBodyInput(e) {
    const input = e.target;
    const plainText = getCleanText(input.value);

    if (plainText.length > MAX_BODY_LEN) {
        // Enforce strict ceiling and prevent typing beyond 240 chars
        input.value = input.value.slice(0, -1);
    }

    const currentPlainLen = getCleanText(input.value).length;
    const remaining = Math.max(0, MAX_BODY_LEN - currentPlainLen);
    const counterEl = document.getElementById("bodyCounter");
    if (counterEl) {
        counterEl.textContent = `${currentPlainLen}/${MAX_BODY_LEN} (${remaining} शेष)`;
        if (remaining === 0) {
            counterEl.className = "char-counter limit-reached";
        } else if (remaining <= 20) {
            counterEl.className = "char-counter warning";
        } else {
            counterEl.className = "char-counter";
        }
    }
    updatePreview();
}

// Real-Time Preview Renderer
function updatePreview() {
    const titleVal = document.getElementById("notifTitle")?.value.trim() || "Sanatan Seva Samiti";
    const bodyVal = document.getElementById("notifBody")?.value.trim() || "यहाँ आपकी सूचना का लाइव प्रारूप दिखाई देगा...";
    const imageVal = document.getElementById("notifImage")?.value.trim() || "";
    const targetVal = document.getElementById("targetScreen")?.value || "Home";
    const extraVal = document.getElementById("extraData")?.value.trim() || "";

    // Render Preview Card
    const previewTitle = document.getElementById("previewTitle");
    const previewBody = document.getElementById("previewBody");
    const bannerImg = document.getElementById("previewBanner");
    const routeBadge = document.getElementById("previewRoute");

    if (previewTitle) previewTitle.innerHTML = sanitizeHTML(titleVal);
    if (previewBody) previewBody.innerHTML = sanitizeHTML(bodyVal);

    if (bannerImg) {
        if (imageVal) {
            bannerImg.src = imageVal;
            bannerImg.style.display = "block";
        } else {
            bannerImg.style.display = "none";
        }
    }

    if (routeBadge) {
        routeBadge.textContent = `🎯 स्क्रीन: ${targetVal}${extraVal ? " (" + extraVal + ")" : ""}`;
    }

    // Update Live Payload JSON Box
    updatePayloadPreview(titleVal, bodyVal, imageVal, targetVal, extraVal);
}

// Sanitize HTML for safe preview
function sanitizeHTML(str) {
    if (!str) return "";
    return str
        .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
        .replace(/on\w+="[^"]*"/g, "");
}

// Live JSON Payload Preview
function updatePayloadPreview(title, body, image, screen, extra) {
    const audienceVal = document.getElementById("targetAudience")?.value || "all_users";
    const payload = {
        message: {
            topic: audienceVal,
            notification: {
                title: title,
                body: getCleanText(body),
                image: image || undefined
            },
            data: {
                title: title,
                body: body,
                imageUrl: image || "",
                screen: screen,
                extra: extra || "",
                timestamp: new Date().toISOString()
            },
            android: {
                priority: "HIGH",
                notification: {
                    channel_id: "sanatanam_updates_channel",
                    notification_priority: "PRIORITY_HIGH",
                    color: "#D93F01",
                    icon: "ic_launcher"
                }
            }
        }
    };

    const payloadBox = document.getElementById("fcmPayloadPreview");
    if (payloadBox) {
        payloadBox.textContent = JSON.stringify(payload, null, 2);
    }
}

// Rich Text Formatting Actions
function insertTag(tag, attr = "") {
    const textarea = document.getElementById("notifBody");
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selectedText = textarea.value.substring(start, end) || "पाठ";

    let openTag = `<${tag}${attr ? " " + attr : ""}>`;
    let closeTag = `</${tag}>`;
    let replacement = `${openTag}${selectedText}${closeTag}`;

    textarea.setRangeText(replacement, start, end, "select");
    textarea.focus();
    handleBodyInput({ target: textarea });
}

function insertLink() {
    const url = prompt("लिंक URL दर्ज करें (e.g. https://sanatansevasamiti.org):", "https://");
    if (url && url !== "https://") {
        insertTag("a", `href="${url}" style="color:#ffd866;"`);
    }
}

function clearFormatting() {
    const textarea = document.getElementById("notifBody");
    if (!textarea) return;
    textarea.value = getCleanText(textarea.value);
    handleBodyInput({ target: textarea });
}

// Load Preset Template
function loadPreset(key) {
    const p = presets[key];
    if (!p) return;

    const titleInput = document.getElementById("notifTitle");
    const bodyInput = document.getElementById("notifBody");
    const imageInput = document.getElementById("notifImage");
    const targetSelect = document.getElementById("targetScreen");
    const extraInput = document.getElementById("extraData");

    if (titleInput) titleInput.value = p.title;
    if (bodyInput) bodyInput.value = p.body;
    if (imageInput) imageInput.value = p.image;
    if (targetSelect) targetSelect.value = p.target;
    if (extraInput) extraInput.value = p.extra;

    if (titleInput) handleTitleInput({ target: titleInput });
    if (bodyInput) handleBodyInput({ target: bodyInput });
    showToast(`टैम्प्लेट "${p.title.substring(0, 20)}..." लोड हो गया!`);
}

// Dispatch Execution & Firestore Save
function handleDispatch() {
    const title = document.getElementById("notifTitle")?.value.trim() || "";
    const body = document.getElementById("notifBody")?.value.trim() || "";
    const image = document.getElementById("notifImage")?.value.trim() || "";
    const screen = document.getElementById("targetScreen")?.value || "Home";
    const audience = document.getElementById("targetAudience")?.value || "all_users";
    const extra = document.getElementById("extraData")?.value.trim() || "";

    if (!title) {
        alert("कृपया सूचना का शीर्षक (Title) दर्ज करें!");
        return;
    }
    if (!body) {
        alert("कृपया सूचना का विवरण (Body) दर्ज करें!");
        return;
    }

    const dispatchRecord = {
        id: "ntf_" + Date.now(),
        title: title,
        body: body,
        imageUrl: image,
        screen: screen,
        audience: audience,
        extraData: extra,
        timestamp: new Date().toLocaleString("hi-IN"),
        status: "Sent"
    };

    // 1. Save to Local History Cache
    const history = JSON.parse(localStorage.getItem("sss_notification_history") || "[]");
    history.unshift(dispatchRecord);
    localStorage.setItem("sss_notification_history", JSON.stringify(history.slice(0, 30)));
    renderHistory();

    // 2. Integration with Firestore & Android Native Notification
    let firestoreSaved = false;
    if (window.AndroidBridge) {
        if (typeof window.AndroidBridge.saveNotificationLog === "function") {
            try {
                window.AndroidBridge.saveNotificationLog(title, body, image, screen, audience, extra);
                firestoreSaved = true;
            } catch (e) {
                console.warn("Firestore logging via AndroidBridge:", e);
            }
        }
        if (typeof window.AndroidBridge.triggerLocalNotification === "function") {
            try {
                window.AndroidBridge.triggerLocalNotification(title, body, image, screen, extra);
            } catch (e) {
                console.warn("Local notification trigger:", e);
            }
        }
    }

    const feedbackMsg = firestoreSaved
        ? "🎉 अधिसूचना प्रेषित व Firestore डेटाबेस में सहेजी गई!"
        : "🎉 अधिसूचना सफलतापूर्वक प्रेषित कर दी गई (Dispatched Successfully)!";
    showToast(feedbackMsg);
}

// Request logs from Firestore
function syncFromFirestore() {
    if (window.AndroidBridge && typeof window.AndroidBridge.fetchNotificationLogs === "function") {
        try {
            window.AndroidBridge.fetchNotificationLogs();
            showToast("Firestore से इतिहास लोड किया जा रहा है...");
        } catch (e) {
            console.warn("Error calling fetchNotificationLogs:", e);
        }
    }
}

// Callback invoked when Android fetches Firestore logs
window.onFirestoreLogsLoaded = function(logs) {
    if (Array.isArray(logs) && logs.length > 0) {
        const formattedLogs = logs.map(l => ({
            id: l.id || ("ntf_" + Math.random()),
            title: l.title || "",
            body: l.body || "",
            imageUrl: l.imageUrl || "",
            screen: l.targetScreen || l.screen || "Home",
            audience: l.audience || "all_users",
            timestamp: l.timestamp || new Date().toLocaleString("hi-IN"),
            status: l.status || "Sent"
        }));

        localStorage.setItem("sss_notification_history", JSON.stringify(formattedLogs));
        renderHistory();
        showToast("Firestore से ताज़ा इतिहास सिंक हो गया!");
    }
};

// Render History Table
function renderHistory() {
    const historyList = document.getElementById("historyList");
    if (!historyList) return;

    const history = JSON.parse(localStorage.getItem("sss_notification_history") || "[]");
    if (history.length === 0) {
        historyList.innerHTML = `<tr><td colspan="5" style="text-align: center; color: rgba(255,255,255,0.5); padding: 20px;">कोई पूर्व प्रेषित सूचना उपलब्ध नहीं है</td></tr>`;
        return;
    }

    historyList.innerHTML = history.map(item => `
        <tr>
            <td>
                <strong>${sanitizeHTML(item.title)}</strong>
                <div style="font-size: 11px; color: rgba(255,255,255,0.6); max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
                    ${sanitizeHTML(item.body)}
                </div>
            </td>
            <td><span class="notif-route-badge" style="margin: 0;">${item.screen || item.targetScreen || 'Home'}</span></td>
            <td>${item.audience}</td>
            <td>${item.timestamp}</td>
            <td>
                <span class="badge-status badge-sent">सफल (${item.status || 'Sent'})</span>
                <button class="btn-action-sm" onclick="reapplyNotification('${item.id}')" style="margin-left: 6px;">पुनः भरें</button>
            </td>
        </tr>
    `).join("");
}

function reapplyNotification(id) {
    const history = JSON.parse(localStorage.getItem("sss_notification_history") || "[]");
    const item = history.find(h => h.id === id);
    if (!item) return;

    const titleInput = document.getElementById("notifTitle");
    const bodyInput = document.getElementById("notifBody");
    const imageInput = document.getElementById("notifImage");
    const targetSelect = document.getElementById("targetScreen");

    if (titleInput) titleInput.value = item.title;
    if (bodyInput) bodyInput.value = item.body;
    if (imageInput) imageInput.value = item.imageUrl || "";
    if (targetSelect) targetSelect.value = item.screen || item.targetScreen || "Home";

    if (titleInput) handleTitleInput({ target: titleInput });
    if (bodyInput) handleBodyInput({ target: bodyInput });
    window.scrollTo({ top: 0, behavior: "smooth" });
    showToast("पूर्व सूचना डेटा फ़ॉर्म में पुनः लोड कर दिया गया!");
}

function clearAllHistory() {
    if (confirm("क्या आप समस्त पूर्व सूचना इतिहास साफ़ करना चाहते हैं?")) {
        localStorage.removeItem("sss_notification_history");
        renderHistory();
        showToast("इतिहास साफ़ कर दिया गया!");
    }
}

// Toast Feedback Notification
function showToast(msg) {
    const toast = document.getElementById("toastBox");
    if (!toast) return;
    toast.textContent = msg;
    toast.style.display = "flex";
    setTimeout(() => {
        toast.style.display = "none";
    }, 3200);
}
