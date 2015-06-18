package com.bmstu.lab3;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.util.Version;
import java.io.*;
/**
 * Root resource (exposed at "myresource" path)
 */
@Path("myresource")
public class MyResource {

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @param data
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getIt(@QueryParam("data") String data) {
        try {
            return Lucene.search("afisha", data);
        }
        catch (IOException e) {
            return "Error";
        }
    }
}

class Lucene {
    
    public static String search(String root, String data) throws IOException {
        Directory dir = FSDirectory.open(new File(root));
        try (IndexReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
            Query q;
            try {
                q = new QueryParser(Version.LUCENE_42, "title", analyzer).parse(data);
            }
            catch (ParseException e) {
                return "Parse Error";
            }
            
            int hitsPerPage = 10;
            TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
            searcher.search(q, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;
            
            String result = "Found " + hits.length + " hits.";
            for(int i=0;i<hits.length;++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                result += "<br>" + ((i + 1) + ". " + d.get("path") + "\t" + d.get("title"));
            }
            
            return result;
        }
        catch (Exception e) {
            return "Reader Error";
        }
    }
}
