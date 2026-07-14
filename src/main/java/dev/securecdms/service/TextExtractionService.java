package dev.securecdms.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class TextExtractionService {

    private final Parser parser = new AutoDetectParser();

    public String extractText(Path filePath) {
        try (InputStream input = Files.newInputStream(filePath)) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            parser.parse(input, handler, metadata, context);
            String text = handler.toString();
            log.debug("Extracted {} characters from {}", text.length(), filePath.getFileName());
            return text;
        } catch (TikaException | SAXException e) {
            log.warn("Tika parse error for {}: {}", filePath.getFileName(), e.getMessage());
            return null;
        } catch (IOException e) {
            log.warn("IO error reading {}: {}", filePath.getFileName(), e.getMessage());
            return null;
        }
    }
}
