package br.com.orcamento.api;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class ExceptionHandlerAdvice {
    @ExceptionHandler(ApiException.class) ResponseEntity<ProblemDetail> api(ApiException e) {return problem(e.status,e.getMessage());}
    @ExceptionHandler({MethodArgumentNotValidException.class,MissingServletRequestParameterException.class,MissingServletRequestPartException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> invalid(Exception e) {return problem(HttpStatus.BAD_REQUEST,"Requisição inválida. Verifique campos, datas ISO e valores no README.");}
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<ProblemDetail> large(Exception e) {return problem(HttpStatus.PAYLOAD_TOO_LARGE,"Arquivo excede o limite de 10 MB.");}
    @ExceptionHandler(Exception.class) ResponseEntity<ProblemDetail> unexpected(Exception e) {return problem(HttpStatus.INTERNAL_SERVER_ERROR,"Falha interna. Consulte o comando antes de repetir uma operação.");}
    private ResponseEntity<ProblemDetail> problem(HttpStatus status,String detail) {return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status,detail));}
}
