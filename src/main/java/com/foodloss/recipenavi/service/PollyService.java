package com.foodloss.recipenavi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.VoiceId;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Base64;

/**
 * Amazon Polly API連携サービス
 * 
 * 調理工程テキストを日本語音声（Mizuki）でMP3に変換し、
 * Base64文字列としてメモリ上で返却する。
 * ファイル保存やS3利用は行わず、コストを最小限に抑える。
 */
@Service
public class PollyService {

    private static final Logger log = LoggerFactory.getLogger(PollyService.class);

    @Value("${aws.region}")
    private String awsRegion;

    /** Amazon Pollyクライアント（スレッドセーフ、シングルトン） */
    private PollyClient pollyClient;

    /**
     * アプリケーション起動時にPollyクライアントを初期化
     */
    @PostConstruct
    public void init() {
        this.pollyClient = PollyClient.builder()
                .region(Region.of(awsRegion))
                .build();
        log.info("Amazon Pollyクライアントを初期化しました（リージョン: {}）", awsRegion);
    }

    /**
     * アプリケーション終了時にPollyクライアントをクリーンアップ
     */
    @PreDestroy
    public void cleanup() {
        if (pollyClient != null) {
            pollyClient.close();
            log.info("Amazon Pollyクライアントをクローズしました");
        }
    }

    /**
     * テキストをMP3音声に変換し、Base64文字列として返却する
     * 
     * @param text 読み上げるテキスト（日本語の調理工程）
     * @return Base64エンコード済みMP3音声データ
     */
    public String synthesizeSpeechToBase64(String text) {
        log.debug("Polly音声生成: {}", text);

        // Pollyリクエストを構築（Mizuki/Standard/MP3）
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                .text(text)
                .voiceId(VoiceId.MIZUKI)        // 日本語女性音声
                .engine(Engine.STANDARD)         // Mizukiはstandardエンジンのみ対応
                .outputFormat(OutputFormat.MP3)   // ブラウザ再生互換フォーマット
                .build();

        try (
            // Polly APIを呼び出し、レスポンスストリームをメモリ上で処理
            ResponseInputStream<SynthesizeSpeechResponse> responseStream =
                    pollyClient.synthesizeSpeech(request)
        ) {
            // MP3バイナリデータをメモリ上で読み取り
            byte[] audioBytes = responseStream.readAllBytes();

            // Base64文字列に変換（ファイル保存なし）
            String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

            log.debug("音声生成完了: {}バイト -> Base64 {}文字", 
                    audioBytes.length, base64Audio.length());

            return base64Audio;

        } catch (Exception e) {
            log.error("Polly音声生成でエラーが発生: {}", text, e);
            throw new RuntimeException("音声の生成に失敗しました: " + e.getMessage(), e);
        }
    }
}
