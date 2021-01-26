package logic;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.*;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static logic.RetrieveJiraTicket.writeJiraCSV;


public class RetrieveCommit {

    static final Logger logger = Logger.getLogger(String.valueOf(RetrieveCommit.class));
    private static final String PROJNAME = "PARQUET";

    static String path = "";
    static String completePath = "";
    static String csvCommitPath = "";
    static String csvJiraPath = "";
    static String csvTemporaryPath = "";
    static String csvFinalPath = "";
    static String csvFilteredFinalPath = "";
    static String counterFilePath = "";

    private static File dir;

    //Conversione da stringa in Date
    public Date stringToDate(String s) {

        Date result = null;
        try {

            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
            result = dateFormat.parse(s);

        } catch (ParseException e) {

            e.printStackTrace();

        }

        return result;

    }

    //Confronto due date
    public Date compareDate(Date date1, Date date2) {

        Date lastDate;

        if (date1.after(date2)) {

            lastDate = date1;

        } else {

            lastDate = date2;

        }

        return lastDate;

    }

    //Clono la repository all'interno di resources
    public static void cloneRepository() throws GitAPIException {

        try(InputStream inputStream = new FileInputStream("C:\\Users\\leona\\Desktop\\Project\\src\\main\\resources\\filepath.properties")) {

            Properties properties = new Properties();
            properties.load(inputStream);

            path = properties.getProperty("path");
            completePath = properties.getProperty("Cpath");
            csvCommitPath = properties.getProperty("csvCommitPath");
            csvJiraPath = properties.getProperty("csvJiraPath");
            csvTemporaryPath = properties.getProperty("csvTemporaryPath");
            csvFinalPath = properties.getProperty("csvFinalPath");
            csvFilteredFinalPath = properties.getProperty("csvFilteredFinalPath");
            counterFilePath = properties.getProperty("counterFilePath");

            dir = new File(path);

        } catch (IOException e) {

            e.printStackTrace();

        }

        if (!dir.exists()) {

            logger.info("Cloning repository");

            if (dir.mkdir()) {

                Git.cloneRepository().setURI("https://github.com/apache/parquet-mr.git").setDirectory(dir).call();
                logger.info("Successful");

            } else {

                logger.info("Directory creation failed");

            }

        }

        //Scrivo tutti i commit nel file commit.csv
        try(FileWriter fileWriter1 = new FileWriter(csvCommitPath);
            CSVWriter csvWriter = new CSVWriter(fileWriter1)) {


            //Impostazione di Git e della repository
            Git git = Git.open(new File(completePath));

            Repository repository = FileRepositoryBuilder.create(new File(completePath));
            String repo = String.valueOf(repository);

            logger.info(repo);

            List<Ref> branches = git.branchList().call();

            for (Ref ref : branches) {

                logger.info(ref.getName());

            }

            Iterable<RevCommit> commits = git.log().all().call();

            //Scorro tutti i commit dalla repo clonata
            for (RevCommit revCommit : commits) {

                String pattern = "MM/dd/yyyy HH:mm:ss";
                DateFormat df = new SimpleDateFormat(pattern);
                String date = df.format(revCommit.getAuthorIdent().getWhen());

                String fullMessage = revCommit.getFullMessage();

                //Inserisco nel file csv solo i commit che rispettano lo standard
                if (fullMessage.startsWith(PROJNAME)) {

                    String shortCommit = fullMessage.substring(0, fullMessage.indexOf(' '));
                    if (shortCommit.contains(":")) {

                        shortCommit = shortCommit.substring(0, shortCommit.length() - 1);

                    }

                    csvWriter.writeNext(new String[]{date, shortCommit});

                }

            }

            csvWriter.flush();

        } catch (IOException e) {

            logger.log(Level.WARNING, String.valueOf(e));

        }

    }

    //Creo un nuovo file csv con l'intersezione dei commit totali e quelli di jira
    public void intersectCsv() {

        try(FileReader fR = new FileReader(csvCommitPath);
            CSVReader csvReader = new CSVReader(fR);
            FileReader fR1 = new FileReader(csvJiraPath);
            CSVReader csvReader1 = new CSVReader(fR1);

            FileWriter fW = new FileWriter(csvTemporaryPath);
            CSVWriter csvWriter = new CSVWriter(fW)) {

            List<String[]> list1 = csvReader.readAll();
            List<String[]> list2 = csvReader1.readAll();

            //Intersezione tra i due file
            for (String[] r : list1) {

                for (String[] s : list2) {

                    if (r[1].equals(s[0])) {

                        //scrivo solo i record contenuti in entrambi
                        csvWriter.writeNext(new String[]{r[0].substring(0, 10), s[0]});

                    }

                }

            }

            csvWriter.flush();
        } catch (IOException e) {

            logger.log(Level.WARNING, String.valueOf(e));

        }

    }

    public void createFinalCsv() throws IOException {

        try(FileReader fR2 = new FileReader(csvTemporaryPath);
            CSVReader csvReader2 = new CSVReader(fR2);

            FileWriter fileWriter = new FileWriter(csvFinalPath);
            CSVWriter csvWriter1 = new CSVWriter(fileWriter)){

            List<String[]> list3;
            list3 = csvReader2.readAll();

            Date date;
            Date date1;
            Date dateFinal;

            //HashMap d'appoggio
            HashMap<String, String> hashMap = new HashMap<>();

            //Prendo soltanto il commit più recente relativo ad un determinato ticket
            for (String[] str : list3) {

                if (!hashMap.containsKey(str[1])) {

                    hashMap.put(str[1], str[0]);

                } else {

                    date = stringToDate(str[0]);
                    date1 = stringToDate(hashMap.get(str[1]));
                    dateFinal = compareDate(date, date1);
                    if (dateFinal == date) {

                        hashMap.put(str[1], str[0]);

                    }

                }

            }

            //write in final csv
            for (Map.Entry<String, String> entry : hashMap.entrySet()) {

                csvWriter1.writeNext(new String[]{entry.getValue(), entry.getKey()});

            }

            csvWriter1.flush();

        }catch(IOException e){

            logger.log(Level.WARNING, String.valueOf(e));

        }

    }

    public void cleanUp(Path path) throws IOException {

        Files.delete(path);

    }

    public int[] filterFinalCSV(){

        int[] integers = new int[2];

        try(FileReader fR= new FileReader(csvFinalPath);
            CSVReader csvReader = new CSVReader(fR);
            FileWriter fileWriter = new FileWriter(csvFilteredFinalPath);
            CSVWriter csvWriter = new CSVWriter(fileWriter);){

            List<String[]> list = csvReader.readAll();
            List<String[]> listToWrite = new ArrayList<>();
            List<Integer> anotherList = new ArrayList<>();

            for(String[] strings: list){

                String string = strings[0].substring(0,3);
                String string1 = strings[0].substring(6, 10);
                String finalString = string + string1;

                listToWrite.add(new String[] {finalString, strings[1]});
                anotherList.add(Integer.parseInt(finalString.substring(3, 7)));

            }

            Collections.sort(anotherList);
            int min = anotherList.get(0);
            int max = anotherList.get(anotherList.size()-1);

            integers = new int[2];
            integers[0] = min;
            integers[1] = max;

            csvWriter.writeAll(listToWrite);

        }catch (IOException e){

            e.printStackTrace();

        }


        //commento
        return integers;

    }

    public void createCounter(int min, int max){

        Map<String, Integer> map = new HashMap<>();

        for(int i = min; i < max + 1; i++){

            for(int j = 1; j < 13; j++){

                if(j > 9){

                    map.put(j + "/" + i, 0);


                }else{

                    map.put("0" + j + "/" + i, 0);


                }

            }

        }

        List<String[]> anotherList = new ArrayList<>();

        try(FileReader fileReader = new FileReader(csvFilteredFinalPath);
            CSVReader csvReader = new CSVReader(fileReader);
            FileWriter fileWriter = new FileWriter(counterFilePath);
            CSVWriter csvWriter = new CSVWriter(fileWriter);){

            List<String[]> list = csvReader.readAll();

            for(String[] strings: list){

                if(map.containsKey(strings[0])){

                    int integer = map.get(strings[0]);
                    map.replace(strings[0],integer+1);

                }

            }

            for(Map.Entry<String, Integer> entry: map.entrySet()){

                anotherList.add(new String[]{entry.getKey(), entry.getValue().toString()});

            }

            List<String[]> sortedList = sortByDate(anotherList);
            csvWriter.writeNext(new String[]{"Date", "Counter"});
            csvWriter.writeAll(sortedList);

        } catch (IOException e) {

            e.printStackTrace();

        }

    }

    private SimpleDateFormat format = new SimpleDateFormat("MM/yyyy");

    private List<String[]> sortByDate(List<String[]> list) {
        /**
         * Questo metodo esegue il sorting della lista
         * andando a considerare la data.
         * Return list sorted.
         */

        list.sort((strings, t1) -> {
            Date date1 = new Date();
            Date date2 = new Date();
            try {
                date1 = format.parse(strings[0]);
                date2 = format.parse(t1[0]);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return date1.compareTo(date2);
        });

        return list;

    }


    public static void main(String[] args) throws GitAPIException, IOException {

        logger.info("Scrivo tutti i commit");
        writeJiraCSV();
        cloneRepository();

        new RetrieveCommit().intersectCsv();
        new RetrieveCommit().createFinalCsv();
        int[] integers = new RetrieveCommit().filterFinalCSV();
        int min = integers[0];
        int max = integers[1];
        new RetrieveCommit().createCounter(min, max);
        new RetrieveCommit().cleanUp(Paths.get(csvTemporaryPath));
        new RetrieveCommit().cleanUp(Paths.get(csvJiraPath));

    }

}
