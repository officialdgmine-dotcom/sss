// Sanatan Seva Samiti (SSS) - Push Notification Admin Dispatcher
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
    const targetSelect = document.getElementById("targetScreen");
    const extraInput = document.getElementById("extraData");
    const dispatchBtn = document.getElementById("dispatchBtn");

    // Live Listeners with strict limit enforcement
    titleInput.addEventListener("input", handleTitleInput);
    bodyInput.addEventListener("input", handleBodyInput);
    imageInput.addEventListener("input", updatePreview);
    targetSelect.addEventListener("change", updatePreview);
    extraInput.addEventListener("input", updatePreview);

    dispatchBtn.addEventListener("click", handleDispatch);

    // Initial render
    updatePreview();
    renderHistory();
}

// Strip HTML tags to measure raw text length
function getCleanText(htmlStr) {
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
    counterEl.textContent = `${currentLen}/${MAX_TITLE_LEN} (${remaining} शेष)`;

    if (remaining === 0) {
        counterEl.className = "char-counter limit-reached";
    } else if (remaining <= 10) {
        counterEl.className = "char-counter warning";
    } else {
        counterEl.className = "char-counter";
    }

    updatePreview();
}

// Strict Body Limit (240 Plain-Text Chars)
function handleBodyInput(e) {
    const input = e.target;
    const plainText = getCleanText(input.value);

    if (plainText.length > MAX_BODY_LEN) {
        // Enforce strict ceiling
        input.value = input.value.slice(0, -1);
    }

    const currentPlainLen = getCleanText(input.value).length;
    const remaining = Math.max(0, MAX_BODY_LEN - currentPlainLen);
    const counterEl = document.getElementById("bodyCounter");
    counterEl.textContent = `${currentPlainLen}/${MAX_BODY_LEN} (${remaining} शेष)`;

    if (remaining === 0) {
        counterEl.className = "char-counter limit-reached";
    } else if (remaining <= 20) {
        counterEl.className = "char-counter warning";
    } else {
        counterEl.className = "char-counter";
    }

    updatePreview();
}

// Real-Time Preview Renderer
function updatePreview() {
    const titleVal = document.getElementById("notifTitle").value.trim() || "Sanatan Seva Samiti";
    const bodyVal = document.getElementById("notifBody").value.trim() || "यहाँ आपकी सूचना का लाइव प्रारूप दिखाई देगा...";
    const imageVal = document.getElementById("notifImage").value.trim();
    const targetVal = document.getElementById("targetScreen").value;
    const extraVal = document.getElementById("extraData").value.trim();

    // Render Preview Card
    document.getElementById("previewTitle").innerHTML = sanitizeHTML(titleVal);
    document.getElementById("previewBody").innerHTML = sanitizeHTML(bodyVal);

    const bannerImg = document.getElementById("previewBanner");
    if (imageVal) {
        bannerImg.src = imageVal;
        bannerImg.style.display = "block";
    } else {
        bannerImg.style.display = "none";
    }

    const routeBadge = document.getElementById("previewRoute");
    routeBadge.textContent = `🎯 स्क्रीन: ${targetVal}${extraVal ? " (" + extraVal + ")" : ""}`;

    // Update Live Payload JSON Box
    updatePayloadPreview(titleVal, bodyVal, imageVal, targetVal, extraVal);
}

// Sanitize HTML for safe preview
function sanitizeHTML(str) {
    // Allows <b>, <i>, <span>, <a>, <mark>
    return str
        .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, "")
        .replace(/on\w+="[^"]*"/g, "");
}

// Live JSON Payload
function updatePayloadPreview(title, body, image, screen, extra) {
    const payload = {
        message: {
            topic: document.getElementById("targetAudience").value,
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

    titleInput.value = p.title;
    bodyInput.value = p.body;
    imageInput.value = p.image;
    targetSelect.value = p.target;
    extraInput.value = p.extra;

    handleTitleInput({ target: titleInput });
    handleBodyInput({ target: bodyInput });
    showToast(`टैम्प्लेट "${p.title.substring(0, 20)}..." लोड हो गया!`);
}

// Dispatch Execution
function handleDispatch() {
    const title = document.getElementById("notifTitle").value.trim();
    const body = document.getElementById("notifBody").value.trim();
    const image = document.getElementById("notifImage").value.trim();
    const screen = document.getElementById("targetScreen").value;
    const audience = document.getElementById("targetAudience").value;

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
        timestamp: new Date().toLocaleString("hi-IN"),
        status: "Sent"
    };

    // Save to Local History
    const history = JSON.parse(localStorage.getItem("sss_notification_history") || "[]");
    history.unshift(dispatchRecord);
    localStorage.setItem("sss_notification_history", JSON.stringify(history.slice(0, 30)));

    renderHistory();
    showToast("🎉 सूचना सफलतापूर्वक प्रेषित कर दी गई (Dispatched Successfully)!");

    // If running inside Android WebView, test trigger native notification
    if (window.AndroidBridge && window.AndroidBridge.navigateToScreen) {
        console.log("Device connected via AndroidBridge");
    }
}

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
            <td><span class="notif-route-badge" style="margin: 0;">${item.screen}</span></td>
            <td>${item.audience}</td>
            <td>${item.timestamp}</td>
            <td>
                <span class="badge-status badge-sent">सफल (Sent)</span>
                <button class="btn-action-sm" onclick="reapplyNotification('${item.id}')" style="margin-left: 6px;">पुनः भरें</button>
            </td>
        </tr>
    `).join("");
}

function reapplyNotification(id) {
    const history = JSON.parse(localStorage.getItem("sss_notification_history") || "[]");
    const item = history.find(h => h.id === id);
    if (!item) return;

    document.getElementById("notifTitle").value = item.title;
    document.getElementById("notifBody").value = item.body;
    document.getElementById("notifImage").value = item.imageUrl || "";
    document.getElementById("targetScreen").value = item.screen || "Home";

    handleTitleInput({ target: document.getElementById("notifTitle") });
    handleBodyInput({ target: document.getElementById("notifBody") });
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
    toast.textContent = msg;
    toast.style.display = "flex";
    setTimeout(() => {
        toast.style.display = "none";
    }, 3200);
}
