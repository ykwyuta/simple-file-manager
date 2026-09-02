package com.example.filemanager.controller;

import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.DuplicateGroupException;
import com.example.filemanager.exception.DuplicateUsernameException;
import com.example.filemanager.exception.FileLockedException;
import com.example.filemanager.exception.GroupNotFoundException;
import com.example.filemanager.exception.InvalidNameException;
import com.example.filemanager.exception.InvalidPermissionFormatException;
import com.example.filemanager.exception.ParentDeletedException;
import com.example.filemanager.exception.ParentNotDirectoryException;
import com.example.filemanager.exception.ResourceNotFoundException;
import com.example.filemanager.exception.UserNotFoundException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps domain exceptions to HTTP status codes for the {@code /api/**} surface.
 *
 * <p>
 * Scoped to {@link RestController}s on purpose. A catch-all
 * {@code @ExceptionHandler(Exception.class)} on every controller swallows
 * Spring's own exception translation, turning 403s, 404s and validation
 * failures alike into 500s — including the 404 for {@code /favicon.ico}.
 *
 * <p>
 * Responses carry a {@link ProblemDetail} with a human-readable message.
 * Unexpected exceptions are logged with their stack trace but answered with a
 * fixed message, so internals such as controller method signatures never reach
 * the client.
 */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ ResourceNotFoundException.class, UserNotFoundException.class,
            GroupNotFoundException.class, NoResourceFoundException.class })
    public ProblemDetail handleNotFound(Exception ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({ DuplicateFileException.class, DuplicateUsernameException.class,
            DuplicateGroupException.class })
    public ProblemDetail handleDuplicate(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(FileLockedException.class)
    public ProblemDetail handleLocked(FileLockedException ex) {
        return problem(HttpStatus.LOCKED, ex.getMessage());
    }

    @ExceptionHandler({ InvalidPermissionFormatException.class, InvalidNameException.class,
            ParentNotDirectoryException.class, ParentDeletedException.class,
            IllegalArgumentException.class, IllegalStateException.class })
    public ProblemDetail handleBadRequest(RuntimeException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, detail.isEmpty() ? "入力内容に誤りがあります。" : detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        logger.warn("Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "その名前は既に使用されています。");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "ファイルサイズが上限を超えています。");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        logger.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "サーバー内部でエラーが発生しました。");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail body = ProblemDetail.forStatus(status);
        body.setDetail(detail == null || detail.isBlank() ? status.getReasonPhrase() : detail);
        return body;
    }
}
