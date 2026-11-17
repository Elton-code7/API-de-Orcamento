package br.com.orcamento.api;
import br.com.orcamento.ai.*;
import br.com.orcamento.domain.*;
import br.com.orcamento.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;
@RestController
@RequestMapping("/api")
public class BudgetController {
    private final AssistantService assistant;
    private final AudioService audio;
    private final CommandStore store;
    private final TransactionService transactions;
    public BudgetController(AssistantService assistant,AudioService audio,CommandStore store,TransactionService transactions) {this.assistant=assistant;this.audio=audio;this.store=store;this.transactions=transactions;}
    @GetMapping("/health") public Map<String,String> health() {return Map.of("status","UP");}
    @PostMapping("/commands/text") public CommandView text(@Valid @RequestBody TextRequest request) {return view(assistant.text(request.text()));}
    @PostMapping(value="/commands/audio",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommandView audio(@RequestPart("file") MultipartFile file) {audio.validate(file);return view(assistant.audio(()->audio.transcribe(file)));}
    @GetMapping("/commands/{id}") public CommandView get(@PathVariable UUID id) {return view(store.get(id));}
    @PostMapping("/commands/{id}/confirm") public CommandView confirm(@PathVariable UUID id) {return view(store.confirm(id));}
    @PostMapping("/commands/{id}/cancel") public CommandView cancel(@PathVariable UUID id) {return view(store.cancel(id));}
    @GetMapping("/commands/{id}/audit") public List<AuditEvent> audit(@PathVariable UUID id) {return store.audit(id);}
    @GetMapping(value="/commands/{id}/speech",produces="audio/mpeg")
    public ResponseEntity<byte[]> speech(@PathVariable UUID id) {
        var c=store.get(id);
        if(c.status==CommandStatus.PROCESSANDO || c.answer==null) throw new ApiException(HttpStatus.CONFLICT,"Comando ainda em processamento.");
        return ResponseEntity.ok().header("Content-Disposition",ContentDisposition.attachment().filename("resposta.mp3").build().toString())
            .header("X-AI-Generated-Audio","true").header("Cache-Control","no-store").body(audio.speak(c.answer));
    }
    @GetMapping("/transactions") public Map<String,Object> list(@RequestParam LocalDate start,@RequestParam LocalDate end,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        var p=transactions.list(start,end,page,size);
        return Map.of("items",p.getContent(),"page",p.getNumber(),"size",p.getSize(),"totalElements",p.getTotalElements(),"totalPages",p.getTotalPages());
    }
    @GetMapping("/transactions/summary") public TransactionService.Summary summary(@RequestParam LocalDate start,@RequestParam LocalDate end) {return transactions.summary(start,end);}
    private CommandView view(CommandRecord c) {return new CommandView(c.id,c.status,c.source,c.transcript,c.answer,store.decode(c.proposalJson),c.transactionId,c.createdAt);}
    public record TextRequest(@NotBlank @Size(max=4000) String text) {}
    public record CommandView(UUID id, CommandStatus status,String source,String transcript,String answer,TransactionInput proposal,UUID transactionId,Instant createdAt) {}
}
