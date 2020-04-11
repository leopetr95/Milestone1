package logic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.opencsv.CSVWriter;

public class RetrieveJiraTicket {

    final static Logger logger = Logger.getLogger(String.valueOf(RetrieveCommit.class));

    static String csvJiraPath= "";

    private static String readAll(Reader rd) throws IOException {

        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {

            sb.append((char) cp);

        }

        return sb.toString();

    }

    public static JSONArray readJsonArrayFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();

        try {

            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONArray json = new JSONArray(jsonText);

            return json;

        } finally {

            is.close();

        }
    }

    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {

        InputStream is = new URL(url).openStream();

        try{

            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONObject json = new JSONObject(jsonText);

            return json;

        }finally{

            is.close();

        }

    }

    public void writeJiraCSV() throws IOException {

        try{

            InputStream inputStream = new FileInputStream("C:\\Users\\leona\\Desktop\\Project\\src\\main\\resources\\filepath.properties");
            Properties properties = new Properties();
            properties.load(inputStream);

            csvJiraPath = properties.getProperty("csvJiraPath");

        }catch (IOException e){

            e.printStackTrace();

        }

        FileWriter fileWriter = new FileWriter(csvJiraPath);
        CSVWriter csvWriter = new CSVWriter(fileWriter);

        List<String[]> dataList = new ArrayList<>();

        String projName ="PARQUET";

        Integer j = 0, i = 0, total = 1;
        //Get JSON API for closed bugs w/ AV in the project

        do {

            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;

            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i.toString() + "&maxResults=" + j.toString();

            JSONObject json = readJsonFromUrl(url);

            JSONArray issues = json.getJSONArray("issues");

            total = json.getInt("total");

            for (; i < total && i < j; i++) {
                //Iterate through each bug
                String key = issues.getJSONObject(i%1000).get("key").toString();

                dataList.add(new String[] {key});

            }

            try{

                csvWriter.writeAll(dataList);
                csvWriter.flush();
                csvWriter.close();

            }catch (IOException e){

                logger.log(Level.WARNING, String.valueOf(e));

            }

        } while (i < total);


    }



}




