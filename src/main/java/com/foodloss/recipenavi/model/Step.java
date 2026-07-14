package com.foodloss.recipenavi.model;

/**
 * 調理工程データモデル
 * 
 * 1工程分の調理手順テキストと、対応するBase64エンコード済み音声データを保持する。
 * テキストとオーディオを1つのオブジェクトとして紐付けることで、
 * フロントエンドが1回のAPIコールで全データを受信できる。
 */
public class Step {

    /** 工程番号（1始まり） */
    private int stepNumber;

    /** 調理手順テキスト */
    private String instruction;

    /** Base64エンコード済みMP3音声データ */
    private String audioBase64;

    // デフォルトコンストラクタ（Jacksonデシリアライズ用）
    public Step() {}

    public Step(int stepNumber, String instruction, String audioBase64) {
        this.stepNumber = stepNumber;
        this.instruction = instruction;
        this.audioBase64 = audioBase64;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getAudioBase64() {
        return audioBase64;
    }

    public void setAudioBase64(String audioBase64) {
        this.audioBase64 = audioBase64;
    }
}
