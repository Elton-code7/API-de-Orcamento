package br.com.orcamento.ai;
import br.com.orcamento.api.ApiException;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@Service
public class AudioService {
    private final OpenAiAudioTranscriptionModel transcription;
    private final OpenAiAudioSpeechModel speech;
    private static final Set<String> EXTENSIONS=Set.of("mp3","mp4","mpeg","mpga","m4a","wav","webm");
    public AudioService(OpenAiAudioTranscriptionModel transcription, OpenAiAudioSpeechModel speech) {this.transcription=transcription;this.speech=speech;}
    public void validate(MultipartFile file) {
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"O arquivo de áudio está vazio.");
        if(file.getSize()>10*1024*1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"Limite de áudio: 10 MB.");
        if(!EXTENSIONS.contains(ext)) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Formato aceito: mp3, mp4, mpeg, mpga, m4a, wav ou webm.");
    }
    public String transcribe(MultipartFile file) {
        validate(file);
        try {
            // Nome controlado, preservando apenas a extensão para o multipart enviado ao provedor.
            String name=Objects.requireNonNull(file.getOriginalFilename());
            String filename="audio."+name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
            return transcription.transcribe(new ByteArrayResource(file.getBytes()) { @Override public String getFilename() {return filename;} });
        } catch(IOException e) {throw new ApiException(HttpStatus.BAD_REQUEST,"Não foi possível ler o áudio.");}
    }
    public byte[] speak(String text) {
        try { return speech.call(text); }
        catch(RuntimeException e) {throw new ApiException(HttpStatus.BAD_GATEWAY,"Falha ao gerar voz. A operação financeira anterior não foi desfeita; tente apenas o endpoint de voz novamente.");}
    }
}
