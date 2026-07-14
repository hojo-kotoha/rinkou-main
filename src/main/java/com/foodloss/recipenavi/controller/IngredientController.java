package com.foodloss.recipenavi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

    private final JdbcTemplate jdbcTemplate;

    public IngredientController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/import")
    public ResponseEntity<String> importCsv(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("CSVファイルが空です");
        }

        int count = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        file.getInputStream(),
                        StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {

                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                String[] columns = line.split(",", -1);

                if (columns.length != 4) {
                    return ResponseEntity.badRequest()
                            .body("CSVの列数が不正です: " + line);
                }

                String name = columns[0].trim();
                int daysLeft = Integer.parseInt(columns[1].trim());
                String quantity = columns[2].trim();
                String storageMethod = columns[3].trim();

                jdbcTemplate.update("""
                        INSERT INTO ingredients
                            (name, days_left, quantity, storage_method)
                        VALUES (?, ?, ?, ?)
                        """,
                        name,
                        daysLeft,
                        quantity,
                        storageMethod
                );

                count++;
            }

            return ResponseEntity.ok(count + "件を登録しました");

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body("残り日数は整数で入力してください");

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("CSV登録に失敗しました: " + e.getMessage());
        }
    }

    @GetMapping
    public List<Map<String, Object>> findAll() {
        return jdbcTemplate.queryForList("""
                SELECT
                    id,
                    name,
                    days_left,
                    quantity,
                    storage_method
                FROM ingredients
                ORDER BY id
                """);
    }
}