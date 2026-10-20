// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.crmsample;

import cn.com.keelbase.annotation.KeelbaseTool;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test fixture: zero-boilerplate declarations paired with hand-written equivalents.
 * Component-scanned into the CRM test context, so both sides are exported side by side.
 *
 * <p>测试夹具：零样板声明与手写等价物成对出现。被 CRM 测试上下文组件扫描，两侧同时导出。
 */
@RestController
@RequestMapping("/api/zero")
class ZeroBoilerplateController {

    /**
     * Search customers by customer id and keyword.
     *
     * @param customerId customer identifier
     * @param keyword    name/company keyword
     */
    @GetMapping("/search")
    @KeelbaseTool(infer = true)
    public List<String> search(long customerId, String keyword) {
        return List.of();
    }

    @GetMapping("/search-handwritten")
    @KeelbaseTool(name = "handwritten_search",
            description = "Search customers by customer id and keyword.")
    public List<String> searchHandwritten(
            @RequestParam(required = false) @Parameter(description = "customer identifier") long customerId,
            @RequestParam(required = false) @Parameter(description = "name/company keyword") String keyword) {
        return List.of();
    }

    /**
     * Search customers by customer id and keyword.
     *
     * @param customerId customer identifier
     * @param keyword    name/company keyword
     */
    @GetMapping("/search-no-infer")
    @KeelbaseTool(name = "no_infer_search")
    public List<String> searchNoInfer(long customerId, String keyword) {
        return List.of();
    }

    /**
     * Search customers by an optional customer id.
     *
     * @param customerId optional customer identifier
     */
    @GetMapping("/optional")
    @KeelbaseTool(name = "optional_query", infer = true)
    public List<String> optionalQuery(@RequestParam Optional<Long> customerId) {
        return List.of();
    }

    /**
     * Create a note from a record body.
     *
     * @param req note request
     */
    @PostMapping("/notes")
    @KeelbaseTool(name = "zero_boilerplate_note", infer = true)
    public Map<String, Object> createNote(@RequestBody ZeroNoteRequest req) {
        return Map.of();
    }

    @PostMapping("/notes-handwritten")
    @KeelbaseTool(name = "handwritten_note",
            description = "Create a note from a record body.")
    public Map<String, Object> createNoteHandwritten(@RequestBody HandwrittenNoteRequest req) {
        return Map.of();
    }

    @PostMapping("/notes-no-infer")
    @KeelbaseTool(name = "no_infer_note",
            description = "Create a note from a record body.")
    public Map<String, Object> createNoteNoInfer(@RequestBody ZeroNoteRequest req) {
        return Map.of();
    }

    /**
     * Legacy endpoint whose complex parameter is a Spring model attribute, not a tool parameter.
     */
    @GetMapping("/legacy")
    @KeelbaseTool(name = "legacy_lookup", infer = true)
    public Map<String, Object> legacy(CrmStore store) {
        return Map.of();
    }

    /**
     * Note request.
     *
     * @param content note text
     * @param dueDate optional due date
     */
    record ZeroNoteRequest(String content, String dueDate) {
    }

    record HandwrittenNoteRequest(
            @Schema(description = "note text") String content,
            @Schema(description = "optional due date") String dueDate) {
    }
}
