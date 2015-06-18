package lab1;

import java.io.*;
import java.text.*;
import java.util.*;
import org.apache.commons.io.*;
import org.json.simple.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

class Afisha {
    
    public Map<String, Schedule> data;
    
    public Afisha() {
        data = new HashMap<>();
    }
    
    public boolean parse() {
        boolean result = true;

        String url = "https://afisha.yandex.ru/msk/places/";
        System.out.println("get: " + url);
        Document doc = JsoupWrapper.get(url);
        if (null == doc)
            return false;

        for (Element link : doc.select(".chooser_places > dd > a")) {
            // какие категории есть на сайте: cinema, theatre, concert и т.д.
            String type = link.attr("href").replaceFirst("^(.*?)category=", "").replaceFirst("&(.*?)$", "");
            if (type.length() > 0) {
                Schedule schedule = new Schedule();
                result &= schedule.parse(type);
                data.put(type, schedule);
            }
        }

        return result;
    }
    
    public void save(String root) {  
        root = FileWrapper.AddTrailingDelimiter(root);     
        try {
            FileUtils.deleteDirectory(new File(root));
        } catch (Exception e) { }
        
        for (String type : data.keySet()) {
            String path = FileWrapper.AddTrailingDelimiter(root + type);
            Schedule schedule = data.get(type);
            
            for (Place place : schedule.data.keySet()) {
                String pathPlaces = FileWrapper.AddTrailingDelimiter(path + "places");
                
                JSONObject jsonPlace = new JSONObject();  
                JSONArray jsonEvents = new JSONArray();
                jsonPlace.put("title", place.title);
                jsonPlace.put("id", place.id);
                jsonPlace.put("info", place.info);
                jsonPlace.put("address", place.address);
                jsonPlace.put("longitude", place.longitude);
                jsonPlace.put("latitude", place.latitude);    
                
                Map<String, Event> map = schedule.data.get(place);
                for (Event event : map.values()) {
                    jsonEvents.add(event.id);
                    String pathEvents = FileWrapper.AddTrailingDelimiter(path + "events");
                    
                    JSONObject jsonEvent = new JSONObject(); 
                    JSONArray jsonDates = new JSONArray();
                    jsonEvent.put("title", event.title);
                    jsonEvent.put("id", event.id);   
                    jsonEvent.put("place", place.id);
                    
                    jsonDates.addAll(event.dates);
                    jsonEvent.put("dates", jsonDates);
                    
                    FileWrapper.save(pathEvents + event.id + ".json", jsonEvent.toString());
                }
                
                jsonPlace.put("events", jsonEvents);
                FileWrapper.save(pathPlaces + place.id + ".json", jsonPlace.toString());
            }
        }
    }
    
}

class Place {
    
    public String id;
    public String type;
    public String title;
    public String info;
    public String address;
    public String longitude;
    public String latitude;
    
}

class Event {
    
    public String id;
    public String type;
    public String title;
    public ArrayList<String> dates;
    
}

class Schedule {
    
    public Map<Place, Map<String, Event> > data; 
            
    Schedule() {
        data = new HashMap<>();
    }
    
    public boolean parse(String type) {
        boolean result = true;
                
        String url = "https://afisha.yandex.ru/msk/places/?limit=1000&category=" + type;
        System.out.println("get: " + url);
        Document doc = JsoupWrapper.get(url);
        if (null == doc)
            return false;
        
        for (Element row : doc.select(".chooser_data tr.place")) {
            Place place = new Place();
            place.type = type;
            place.id = row.select("td:first-of-type > a").attr("href").replaceFirst("^(.*?)(?!.*/\\d+)", "").replaceFirst("/(.*?)$", "");
            place.longitude = row.select("td:nth-of-type(2) .longitude").text();
            place.latitude = row.select("td:nth-of-type(2) .latitude").text();
            place.address = row.select("td:nth-of-type(2)" + (place.longitude.length() != 0 ? " > a" : "")).text();        
            result &= parsePlace(place);
        }
        
        return result;
    }
    
    private boolean parsePlace(Place place) {
        boolean result = true;      

        String url = "https://afisha.yandex.ru/msk/places/" + place.id + "/?days=100";
        System.out.println("get: " + url);
        Document doc = JsoupWrapper.get(url);
        if (null == doc)
            return false;
        
        place.title = doc.select(".b-place__name").text();  
        place.info = doc.select(".b-place__charact-list").text();         
        
        // Yandex геокодинг
        if (place.longitude.length() > 0 && place.latitude.length() > 0) {
            url = "http://geocode-maps.yandex.ru/1.x/?ll=" + place.longitude + "," + place.latitude + "&geocode=" + place.address;
            Document geo = JsoupWrapper.get(url);
            if (geo != null)
                place.address = JsoupWrapper.get(url).select("featureMember:first-of-type GeocoderMetaData text").text();
        }
        
        Map<String, Event> map = new HashMap<>();
        Date today = new Date();
        String date = new SimpleDateFormat("yyyy.MM.dd").format(today);
        
        for (Element row : doc.select("#js-schedule-table tr")) {
            if (row.className().equals("b-schedule-table__dayheader")) {
                date = row.text();

                int y = Integer.parseInt(new SimpleDateFormat("yyyy").format(today));
                int m1 = Integer.parseInt(new SimpleDateFormat("MM").format(today));
                int m2 = months.indexOf(date.replaceFirst("^\\d+\\s", "").replaceFirst(",(.*?)$", "")) + 1;
                int d = Integer.parseInt(date.replaceFirst("\\s(.*?)$", ""));
                
                String year = Integer.toString(m2 >= m1 ? y : y + 1);
                String month = (m2 < 10 ? "0" : "") + Integer.toString(m2);
                String day = (d < 10 ? "0": "") + Integer.toString(d);
                
                date = year + "." + month + "." + day;
                continue;
            }
            
            if (row.select(".b-schedule-table__eventinfo").size() == 0)
                continue;
            
            Event event;
            String id = row.select("td:first-of-type > a").attr("href").replaceFirst("^(.*?)(?!.*/\\d+)", "").replaceFirst("/(?!.*/)(.*?)$", "");
            if (map.containsKey(id)) {
                event = map.get(id);
            }
            else {
                event = new Event();
                event.dates = new ArrayList<>();
                event.id = id;
                event.type = place.type;
                event.title = row.select("td:first-of-type > a").text();
            }
            
            Elements times = row.select("td:nth-of-type(2) span > span");
            for (Element time : times)
                event.dates.add(date + " " + time.text().replaceAll("[^\\d:]", ""));
            
            if (times.size() == 0)
                event.dates.add(date + " " + row.select("td:nth-of-type(2)").text().replaceAll("[^\\d:]", ""));
            
            map.put(id, event);
        }
        
        data.put(place, map);
        return result;
    }
    
    private static final List<String> months = new ArrayList<>(Arrays.asList(
        new String[]{"января","февраля","марта","апреля","мая","июня","июля","августа","сентября","октября","ноября","декабря"}
    ));
    
}

public class Lab1 {

    public static void main(String[] args) {
        Afisha afisha = new Afisha();        
        
        if (afisha.parse()) {
            System.out.println("Status: OK.");
            afisha.save("afisha");
        }
        else {
            System.out.println("Status: Error.");
        }
    }
    
}

class JsoupWrapper {
    
    public static Document get(String url) {
        for (int i = 5; i > 0; --i) {
            try {
                return Jsoup.connect(url).get();
            }
            catch (Exception e) { 
                try {
                    Thread.sleep(200);
                }
                catch (InterruptedException ie) { }
            }
        }
        return null;
    }
    
}

class FileWrapper {
    
    public static boolean save(String path, String str) {
        System.out.println("save: " + path);
        (new File(path)).getParentFile().mkdirs();
        try (Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "utf-8"))) {
            out.write(str);
            return true;
        } catch (Exception e) { 
            return false;
        }
    }
    
    public static String AddTrailingDelimiter(String path) {
        if (path.charAt(path.length() - 1) != File.separatorChar)
            path += File.separator;
        return path;
    }
    
}
