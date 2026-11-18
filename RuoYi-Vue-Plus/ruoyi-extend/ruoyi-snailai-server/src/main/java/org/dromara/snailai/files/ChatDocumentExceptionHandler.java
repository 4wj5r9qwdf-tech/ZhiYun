package org.dromara.snailai.files;

import com.aizuda.snail.ai.common.execption.SnailAiAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import org.dromara.snailai.knowledge.KnowledgeDraftController;

/** 仅处理本接口的鉴权和参数错误，不改变框架其他接口的返回契约。 */
@RestControllerAdvice(assignableTypes = {ChatDocumentController.class, KnowledgeDraftController.class})
public class ChatDocumentExceptionHandler {
    @ExceptionHandler(SnailAiAuthenticationException.class)
    public ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未登录或登录已过期"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
