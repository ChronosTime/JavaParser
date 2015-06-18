package lab2;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.json.simple.*;
import org.json.simple.parser.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.nio.file.attribute.*;

public class Lab2 {

    public static void main(String[] args) {     
        try {
            Lucene.rebuild("afisha");
            System.out.println("Status: OK.");
        }
        catch (Exception e) { 
            System.out.println("Status: Error.");
        }
    }
    
}

class Lucene {
    
    public static void rebuild(String root) throws IOException {
        Path path = Paths.get(root);
        Directory dir = FSDirectory.open(path);
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        try (IndexWriter writer = new IndexWriter(dir, config)) {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            index(writer, file, attrs.lastModifiedTime().toMillis());
                        } catch (IOException ignore) {
                            // don't index files that can't be read.
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            else {
                index(writer, path, Files.getLastModifiedTime(path).toMillis());
            }
        }
    }

    public static void index(IndexWriter writer, Path file, long lastModified) throws IOException {
        JSONParser parser = new JSONParser();
        Object obj;
        JSONObject json;
        try {
            json = (JSONObject)parser.parse(new FileReader(file.toString()));
        }
        catch (Exception e) {
            return;
        }
        
        Document doc = new Document();        
        doc.add(new StringField("path", file.toString(), Field.Store.YES));
        doc.add(new LongField("modified", lastModified, Field.Store.NO));
        doc.add(new StringField("title", (String)json.get("title"), Field.Store.YES));

        // Event

        JSONArray dates = (JSONArray)json.get("dates");
        if (null != dates) {
            Iterator<String> iterator = dates.iterator();
            while (iterator.hasNext()) {
                doc.add(new StringField("date", iterator.next(), Field.Store.YES));
            }
        }

        // Place

        obj = json.get("address");
        if (null != obj) {
            doc.add(new StringField("address", (String)obj, Field.Store.YES));
        }
        
        obj = json.get("latitude");
        if (null != obj) {
            doc.add(new StringField("latitude", (String)obj, Field.Store.YES));
        }
        
        obj = json.get("longitude");
        if (null != obj) {
            doc.add(new StringField("longitude", (String)obj, Field.Store.YES));
        }
        
        obj = json.get("info");
        if (null != obj) {
            doc.add(new StringField("info", (String)obj, Field.Store.YES));
        }

        writer.addDocument(doc);
    }
    
}