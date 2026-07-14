/**
 * AIフードロス削減レシピナビ - フロントエンドアプリケーション
 * 
 * Web Speech API による音声入力、REST API通信、
 * レシピタブ切り替え、ステップナビゲーション、Base64音声再生を制御する。
 */

// ==========================================
// グローバル状態管理
// ==========================================

/** 現在のレシピデータ（APIレスポンス） */
let recipeData = null;

/** 現在選択中のレシピインデックス */
let currentRecipeIndex = 0;

/** 現在の調理工程インデックス（0始まり） */
let currentStepIndex = 0;

/** Web Speech API の認識インスタンス */
let recognition = null;

/** 音声認識中かどうか */
let isRecording = false;

/** 現在再生中のAudioオブジェクト */
let currentAudio = null;

// ==========================================
// DOM要素の取得
// ==========================================
const micButton = document.getElementById('mic-button');
const voiceStatus = document.getElementById('voice-status');
const speechText = document.getElementById('speech-text');
const csvFile = document.getElementById('csv-file');
const csvStatus = document.getElementById('csv-status');
const generateButton = document.getElementById('generate-button');
const loadingOverlay = document.getElementById('loading-overlay');
const loadingText = document.getElementById('loading-text');

const recipeSection = document.getElementById('recipe-section');
const recipeTabs = document.getElementById('recipe-tabs');
const recipeCards = document.getElementById('recipe-cards');

const stepSection = document.getElementById('step-section');
const progressFill = document.getElementById('progress-fill');
const progressText = document.getElementById('progress-text');
const stepNumber = document.getElementById('step-number');
const stepInstruction = document.getElementById('step-instruction');
const prevButton = document.getElementById('prev-button');
const nextButton = document.getElementById('next-button');
const listenButton = document.getElementById('listen-button');

// ==========================================
// 初期化
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    initSpeechRecognition();
    bindEvents();
});

// ==========================================
// Web Speech API（音声認識）
// ==========================================

/**
 * Web Speech APIの初期化
 * ブラウザがサポートしていない場合はマイクボタンを無効化する
 */
function initSpeechRecognition() {
    // ブラウザ互換性チェック（Chrome: webkitSpeechRecognition）
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
        voiceStatus.textContent = '⚠️ お使いのブラウザは音声入力に対応していません（Chrome推奨）';
        micButton.disabled = true;
        micButton.style.opacity = '0.4';
        return;
    }

    recognition = new SpeechRecognition();

    // 音声認識の設定
    recognition.lang = 'ja-JP';          // 日本語認識
    recognition.continuous = true;        // 連続認識モード
    recognition.interimResults = true;    // 中間結果を取得（リアルタイム表示用）

    // --- 認識結果イベント ---
    recognition.onresult = (event) => {
        let finalTranscript = '';
        let interimTranscript = '';

        // 認識結果を走査し、確定テキストと中間テキストを分離
        for (let i = event.resultIndex; i < event.results.length; i++) {
            const transcript = event.results[i][0].transcript;
            if (event.results[i].isFinal) {
                finalTranscript += transcript;
            } else {
                interimTranscript += transcript;
            }
        }

        // 確定テキストをテキストエリアに追加
        if (finalTranscript) {
            speechText.value += finalTranscript;
        }

        // 中間結果をステータスに表示（リアルタイムフィードバック）
        if (interimTranscript) {
            voiceStatus.textContent = `🎧 認識中: ${interimTranscript}`;
        }
    };

    // --- 認識終了イベント ---
    recognition.onend = () => {
        // 録音状態を解除（ユーザーが停止した場合、またはタイムアウト）
        stopRecording();
    };

    // --- エラーイベント ---
    recognition.onerror = (event) => {
        console.error('音声認識エラー:', event.error);
        
        if (event.error === 'not-allowed') {
            voiceStatus.textContent = '⚠️ マイクへのアクセスが拒否されました。ブラウザの設定をご確認ください';
        } else if (event.error === 'no-speech') {
            voiceStatus.textContent = '🔇 音声が検出されませんでした。もう一度お試しください';
        } else {
            voiceStatus.textContent = `⚠️ エラーが発生しました: ${event.error}`;
        }
        
        stopRecording();
    };
}

/**
 * 音声認識の開始/停止をトグルする
 */
function toggleRecording() {
    if (isRecording) {
        recognition.stop();
        stopRecording();
    } else {
        startRecording();
    }
}

/**
 * 音声認識を開始する
 */
function startRecording() {
    if (!recognition) return;

    try {
        recognition.start();
        isRecording = true;
        micButton.classList.add('recording');
        voiceStatus.classList.add('recording');
        voiceStatus.textContent = '🔴 録音中... 食材と残り日数を話してください';
    } catch (e) {
        console.error('音声認識開始エラー:', e);
    }
}

/**
 * 音声認識を停止する
 */
function stopRecording() {
    isRecording = false;
    micButton.classList.remove('recording');
    voiceStatus.classList.remove('recording');
    voiceStatus.textContent = 'マイクボタンを押して話してください';
}

// ==========================================
// イベントバインド
// ==========================================

function bindEvents() {
    // マイクボタン
    micButton.addEventListener('click', toggleRecording);

    csvFile.addEventListener('change', handleCsvFile);

    // レシピ生成ボタン
    generateButton.addEventListener('click', handleGenerateRecipes);

    // ステップナビゲーション
    prevButton.addEventListener('click', () => navigateStep(-1));
    nextButton.addEventListener('click', () => navigateStep(1));
    listenButton.addEventListener('click', playCurrentStepAudio);
}

// ==========================================
// CSVファイル読み込み・DB登録
// ==========================================

async function handleCsvFile(event) {
    const file = event.target.files[0];

    if (!file) {
        return;
    }

    if (!file.name.toLowerCase().endsWith('.csv')) {
        csvStatus.textContent = '⚠️ CSVファイルを選択してください';
        csvFile.value = '';
        return;
    }

    csvStatus.textContent =
        `⏳ ${file.name}を読み込んでいます...`;

    try {
        const arrayBuffer = await file.arrayBuffer();

        const csvText = decodeCsvFile(arrayBuffer);

        const ingredientsText =
            convertCsvToIngredientsText(csvText);

        // テキスト欄へ反映
        speechText.value = ingredientsText;

        // バックエンドAPIへCSV送信
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch('/api/ingredients/import', {
            method: 'POST',
            body: formData
        });

        const message = await response.text();

        if (!response.ok) {
            throw new Error(message);
        }

        csvStatus.textContent =
            `✅ ${file.name}を読み込み、DBへ登録しました（${message}）`;

        voiceStatus.textContent =
            'CSVから食材をDBへ登録しました。内容を確認してください';

    } catch (error) {
        console.error('CSV読み込み・登録エラー:', error);

        csvStatus.textContent =
            `⚠️ CSVの読み込みまたはDB登録に失敗しました: ${error.message}`;
    }
}

function decodeCsvFile(arrayBuffer) {
    try {
        const utf8Decoder = new TextDecoder('utf-8', {
            fatal: true
        });

        return utf8Decoder.decode(arrayBuffer);

    } catch (error) {
        const shiftJisDecoder = new TextDecoder('shift_jis');
        return shiftJisDecoder.decode(arrayBuffer);
    }
}

function convertCsvToIngredientsText(csvText) {
    const lines = csvText
        .replace(/^\uFEFF/, '')
        .split(/\r?\n/)
        .map(line => line.trim())
        .filter(line => line !== '');

    if (lines.length < 2) {
        throw new Error('食材データがありません');
    }

    const headers = lines[0]
        .replace(/^"|"$/g, '')
        .split(',')
        .map(header => header.trim().replace(/^"|"$/g, ''));

    const requiredHeaders = [
        '食材名',
        '残り日数',
        '数量',
        '保存方法'
    ];

    for (const requiredHeader of requiredHeaders) {
        if (!headers.includes(requiredHeader)) {
            throw new Error(
                `「${requiredHeader}」列がありません`
            );
        }
    }

    const nameIndex = headers.indexOf('食材名');
    const daysIndex = headers.indexOf('残り日数');
    const quantityIndex = headers.indexOf('数量');
    const storageIndex = headers.indexOf('保存方法');

    const ingredients = [];

    for (let i = 1; i < lines.length; i++) {
        const columns = lines[i]
            .replace(/^"|"$/g, '')
            .split(',')
            .map(column => column.trim().replace(/^"|"$/g, ''));

        const name = columns[nameIndex];
        const days = columns[daysIndex];
        const quantity = columns[quantityIndex];
        const storage = columns[storageIndex];

        if (!name || !days || !quantity || !storage) {
            throw new Error(`${i + 1}行目のデータが不正です`);
        }

        ingredients.push(
            `${name}（残り${days}日、数量：${quantity}、保存方法：${storage}）`
        );
    }

    return ingredients.join('\n');
}

// ==========================================
// API通信（レシピ生成）
// ==========================================

/**
 * レシピ生成ボタンのクリックハンドラ
 * バックエンドAPIに音声テキストを送信し、レシピ＋音声データを取得する
 */
async function handleGenerateRecipes() {
    const text = speechText.value.trim();

    // 入力バリデーション
    if (!text) {
        speechText.focus();
        speechText.classList.add('shake');
        setTimeout(() => speechText.classList.remove('shake'), 500);
        voiceStatus.textContent = '⚠️ 食材を入力してください（音声またはテキスト）';
        return;
    }

    // 音声認識を停止
    if (isRecording) {
        recognition.stop();
    }

    // ローディング表示
    showLoading();

    try {
        // バックエンドAPIにPOSTリクエスト
        const response = await fetch('/api/recipes/generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json; charset=UTF-8',
            },
            body: JSON.stringify({ speechText: text }),
        });

        if (!response.ok) {
            throw new Error(`サーバーエラー: ${response.status}`);
        }

        // レスポンスJSONをパース
        recipeData = await response.json();

        // レシピが空でないか確認
        if (!recipeData.recipes || recipeData.recipes.length === 0) {
            throw new Error('レシピが生成されませんでした');
        }

        // UI更新
        currentRecipeIndex = 0;
        currentStepIndex = 0;
        renderRecipeTabs();
        renderRecipeCard(0);
        showRecipeSection();

    } catch (error) {
        console.error('レシピ生成エラー:', error);
        alert(`レシピの生成に失敗しました。\n\n${error.message}\n\n再度お試しください。`);
    } finally {
        hideLoading();
    }
}

// ==========================================
// ローディング制御
// ==========================================

function showLoading() {
    loadingOverlay.classList.add('active');
    generateButton.disabled = true;

    // ローディングテキストのアニメーション
    const messages = [
        'AIがレシピを考えています...',
        '食材の組み合わせを分析中...',
        '最適なレシピを選定中...',
        '調理工程を生成中...',
        '音声ナビを準備中...',
    ];
    let msgIndex = 0;

    window._loadingInterval = setInterval(() => {
        msgIndex = (msgIndex + 1) % messages.length;
        loadingText.textContent = messages[msgIndex];
    }, 2500);
}

function hideLoading() {
    loadingOverlay.classList.remove('active');
    generateButton.disabled = false;
    if (window._loadingInterval) {
        clearInterval(window._loadingInterval);
    }
}

// ==========================================
// レシピ表示
// ==========================================

/**
 * レシピタブを生成・表示する
 */
function renderRecipeTabs() {
    recipeTabs.innerHTML = '';

    recipeData.recipes.forEach((recipe, index) => {
        const tab = document.createElement('button');
        tab.className = `tab ${index === 0 ? 'active' : ''}`;
        tab.role = 'tab';
        tab.id = `tab-${index}`;
        tab.setAttribute('aria-selected', index === 0);
        tab.textContent = `🍽️ ${recipe.name}`;

        tab.addEventListener('click', () => {
            // タブの切り替え
            document.querySelectorAll('.tab').forEach(t => {
                t.classList.remove('active');
                t.setAttribute('aria-selected', false);
            });
            tab.classList.add('active');
            tab.setAttribute('aria-selected', true);

            currentRecipeIndex = index;
            currentStepIndex = 0;
            renderRecipeCard(index);
        });

        recipeTabs.appendChild(tab);
    });
}

/**
 * 指定インデックスのレシピカードを描画する
 */
function renderRecipeCard(index) {
    const recipe = recipeData.recipes[index];
    recipeCards.innerHTML = '';

    const card = document.createElement('div');
    card.className = 'recipe-card active card card--glass';
    card.id = `recipe-card-${index}`;

    // レシピ名 & 説明
    card.innerHTML = `
        <h3 class="recipe-card__name">${escapeHtml(recipe.name)}</h3>
        <p class="recipe-card__description">${escapeHtml(recipe.description)}</p>

        <!-- 消費期限が近い食材 -->
        <div class="ingredient-section">
            <p class="ingredient-section__title">⏰ 消費期限が近い食材</p>
            <div class="ingredient-tags" id="expiring-tags-${index}"></div>
        </div>

        <!-- 別途必要な調味料 -->
        <div class="ingredient-section">
            <p class="ingredient-section__title">🧂 別途必要な調味料</p>
            <div class="ingredient-tags" id="additional-tags-${index}"></div>
        </div>

        <!-- 調理開始ボタン -->
        <button class="btn btn--start-cooking" id="start-cooking-${index}" type="button">
            🔥 この料理を作る（調理ナビ開始）
        </button>
    `;

    recipeCards.appendChild(card);

    // 消費期限が近い食材タグを生成
    const expiringContainer = document.getElementById(`expiring-tags-${index}`);
    if (recipe.expiringIngredients) {
        recipe.expiringIngredients.forEach(ing => {
            const tag = document.createElement('span');
            const isUrgent = ing.daysLeft <= 1;
            tag.className = `ingredient-tag ingredient-tag--expiring ${isUrgent ? 'urgent' : ''}`;
            tag.textContent = `${ing.name}（残り${ing.daysLeft}日）`;
            expiringContainer.appendChild(tag);
        });
    }

    // 別途必要な調味料タグを生成
    const additionalContainer = document.getElementById(`additional-tags-${index}`);
    if (recipe.additionalIngredients) {
        recipe.additionalIngredients.forEach(name => {
            const tag = document.createElement('span');
            tag.className = 'ingredient-tag ingredient-tag--additional';
            tag.textContent = name;
            additionalContainer.appendChild(tag);
        });
    }

    // 調理開始ボタンのイベント
    document.getElementById(`start-cooking-${index}`).addEventListener('click', () => {
        currentStepIndex = 0;
        showStepSection();
        renderStep();
        // 最初の工程の音声を自動再生
        setTimeout(() => playCurrentStepAudio(), 500);
    });
}

/**
 * レシピセクションを表示する
 */
function showRecipeSection() {
    recipeSection.classList.remove('hidden');
    // セクションにスクロール
    recipeSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// ==========================================
// ステップバイステップ・ナビゲーション
// ==========================================

/**
 * ステップセクションを表示する
 */
function showStepSection() {
    stepSection.classList.remove('hidden');
    stepSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/**
 * 現在の工程を描画する
 */
function renderStep() {
    const recipe = recipeData.recipes[currentRecipeIndex];
    const steps = recipe.steps;
    const step = steps[currentStepIndex];

    // 工程番号とテキストを更新
    stepNumber.textContent = step.stepNumber;
    stepInstruction.textContent = step.instruction;

    // プログレスバーを更新
    const progress = ((currentStepIndex + 1) / steps.length) * 100;
    progressFill.style.width = `${progress}%`;
    progressText.textContent = `工程 ${currentStepIndex + 1} / ${steps.length}`;

    // ボタンの有効/無効を制御
    prevButton.disabled = currentStepIndex === 0;
    nextButton.disabled = currentStepIndex === steps.length - 1;

    // 工程表示のアニメーション
    const display = document.getElementById('step-display');
    display.style.animation = 'none';
    // リフロー強制でアニメーションを再トリガー
    display.offsetHeight;
    display.style.animation = 'fadeInUp 0.4s ease';
}

/**
 * 工程を前後に移動する
 * @param {number} direction -1（前へ）または 1（次へ）
 */
function navigateStep(direction) {
    const recipe = recipeData.recipes[currentRecipeIndex];
    const newIndex = currentStepIndex + direction;

    // 範囲チェック
    if (newIndex < 0 || newIndex >= recipe.steps.length) return;

    // 現在再生中の音声を停止
    stopCurrentAudio();

    currentStepIndex = newIndex;
    renderStep();

    // 工程切替時に音声を自動再生
    setTimeout(() => playCurrentStepAudio(), 300);
}

// ==========================================
// 音声再生（Base64 → Audio）
// ==========================================

/**
 * 現在の工程のBase64音声データを再生する
 */
function playCurrentStepAudio() {
    const recipe = recipeData.recipes[currentRecipeIndex];
    const step = recipe.steps[currentStepIndex];

    if (!step.audioBase64) {
        console.warn('この工程に音声データがありません');
        return;
    }

    // 再生中の音声があれば停止
    stopCurrentAudio();

    // Base64データからAudioオブジェクトを生成
    const audioSrc = `data:audio/mpeg;base64,${step.audioBase64}`;
    currentAudio = new Audio(audioSrc);

    // 再生開始時のUI更新
    listenButton.classList.add('playing');

    currentAudio.play().catch(err => {
        console.error('音声再生エラー:', err);
    });

    // 再生終了時のUI更新
    currentAudio.onended = () => {
        listenButton.classList.remove('playing');
        currentAudio = null;
    };

    currentAudio.onerror = () => {
        listenButton.classList.remove('playing');
        currentAudio = null;
    };
}

/**
 * 現在再生中の音声を停止する
 */
function stopCurrentAudio() {
    if (currentAudio) {
        currentAudio.pause();
        currentAudio.currentTime = 0;
        currentAudio = null;
        listenButton.classList.remove('playing');
    }
}

// ==========================================
// ユーティリティ
// ==========================================

/**
 * HTML特殊文字をエスケープする（XSS対策）
 * @param {string} text エスケープ対象のテキスト
 * @returns {string} エスケープ済みテキスト
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
