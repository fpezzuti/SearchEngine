package it.unipi.dii.aide.mircv.common.beans;

import it.unipi.dii.aide.mircv.common.config.CollectionSize;

import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;


/**
 * Entry of the vocabulary for a term
 */
public class VocabularyEntry{


    /**
     * incremental counter of the terms, used to assign the termid
     */
    private static int termCount = 0;

    /**
     * termid of the specific term
     */
    private int termid;

    /**
     * Term to which refers the vocabulary entry
     */
    private String term;

    /**
     * Document frequency of the term
     */
    private int df = 0;

    /**
     * term frequency of the term in the whole collection
     */
    private int tf = 0;

    /**
     * inverse of document frequency of the term
     */
    private double idf = 0;

    /**
     * starting point of the term's posting list in the inverted index in bytes
     */
    private long memoryOffset = 0;

    /**
     * Starting point of the frequencies in the inverted index in bytes
     */
    private long frequencyOffset = 0;

    /**
     * size of the term's posting list in the inverted index in bytes
     */
    private long memorySize = 0;

    /**
     size of the term; if a term is greater than this size it'll be truncated
     */
    public static final int TERM_SIZE = 64;

    /**
     * term size + 4 + 4 + 8 + 8 + 8 + 8
     * we have to store 2 ints, 1 double and 3 longs
     */
    public static final long ENTRY_SIZE = TERM_SIZE + 4 + 4 + 8 + 8 + 8 + 8;

    /**
     * Constructor for the vocabulary entry
     * create an empty class
     */
    public VocabularyEntry(){}

    /**
     * Constructor for the vocabulary entry for the term passed as parameter
     * Assign the termid to the term and initializes all the statistics and memory information
     * @param term the token of the entry
     */

    public VocabularyEntry(String term){

        // Assign the term
        this.term = term;

        // Assign the termid and increase the counter
        this.termid = termCount;
        termCount ++;
    }

    /**
     *
     * s the statistics of the vocabulary:
     * updates tf and df with the data of the partial posting list processed
     * @param list the posting list from which the method computes the statistics
     */
    public void updateStatistics(PostingList list){

        //for each element of the intermediate posting list
        for(Map.Entry<Integer, Integer> posting: list.getPostings()){

            // update the term frequency
            this.tf += posting.getValue();

            // update the raw document frequency
            this.df++;
        }
    }

    /**
     * Compute the idf using the values computed during the merging of the indexes
     */
    public void computeIDF(){
        this.idf = Math.log10(CollectionSize.getCollectionSize()/(double)this.df);
    }

    /**
     * Returns the vocabulary entry as a string formatted in the following way:
     * [termid]-[term]-[idf] [tf] [memoryOffset] [memorySize]\n
     * @return the formatted string
     */
    public String toString(){
        //format the string for the vocabulary entry
        return term + "->" +
                        tf + " " + df + " "+
                        idf + " " +
                        memoryOffset + " " + frequencyOffset + " " +
                        memorySize +
                        '\n';
    }

    public void setMemorySize(long memorySize) {
        this.memorySize = memorySize;
    }

    public void setMemoryOffset(long memoryOffset) {this.memoryOffset = memoryOffset;
    }

    public void setFrequencyOffset(long freqOffset) {
        this.frequencyOffset = freqOffset;
    }

    public void setDf(int df) {
        this.df = df;
    }

    public static long getENTRY_SIZE() {return ENTRY_SIZE;}

    /**
     * @param PATH : path of file to write entry on
    * @param position : position to start writing from
     * @return  offset representing the position of the last written byte
     * */
    public long writeEntryToDisk(long position, String PATH){
        try (FileChannel fChan = (FileChannel) Files.newByteChannel(
                Paths.get(PATH),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ)) {


            // instantiation of MappedByteBuffer
            try {
                MappedByteBuffer buffer = fChan.map(FileChannel.MapMode.READ_WRITE, position, ENTRY_SIZE);

                // Buffer not created
                if (buffer == null)
                    return -1;

                //allocate char buffer to write term
                CharBuffer charBuffer = CharBuffer.allocate(TERM_SIZE);

                //populate char buffer char by char
                for (int i = 0; i < term.length(); i++)
                    charBuffer.put(i, term.charAt(i));

                // Write the term into file
                buffer.put(StandardCharsets.UTF_8.encode(charBuffer));

                // Write the document frequency into file
                buffer.putInt(this.getDf());

                // Write the term frequency into file
                buffer.putInt(this.getTf());

                //wirte IDF into file
                buffer.putDouble(this.getIdf());

                //write memory offset into file
                buffer.putLong(this.getMemoryOffset());

                //write frequency offset into file
                buffer.putLong(this.getFrequencyOffset());

                //write memory offset into file
                buffer.putLong(this.getMemorySize());


                // return position for which we have to start writing on file
                return position + ENTRY_SIZE;
            }
            catch (Exception e){
                e.printStackTrace();
                return -1;
            }



        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

/**
 * Read the document index entry from disk
 * @param memoryOffset the memory offset from which we start reading
 * @param PATH path of the file on disk
 * @return the position of the last byte read
*/
public long readFromDisk(long memoryOffset,String PATH){
// try to open a file channel to the file of the inverted index
try (FileChannel fChan = (FileChannel) Files.newByteChannel(
    Paths.get(PATH),
    StandardOpenOption.WRITE,
    StandardOpenOption.READ,
    StandardOpenOption.CREATE)) {

    // instantiation of MappedByteBuffer for the PID read
    MappedByteBuffer buffer = fChan.map(FileChannel.MapMode.READ_ONLY, memoryOffset, ENTRY_SIZE);

    // Buffer not created
    if(buffer == null)
        return -1;

    // Read from file into the charBuffer, then pass to the string
    CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer);

    String[] encodedTerm = charBuffer.toString().split("\0");
    if(encodedTerm.length == 0) // TODO: no more entries to read
        return 0;

    this.term = encodedTerm[0];


    // Instantiate the buffer for reading other information
    buffer = fChan.map(FileChannel.MapMode.READ_WRITE, memoryOffset + TERM_SIZE, ENTRY_SIZE - TERM_SIZE);

    // Buffer not created
    if(buffer == null)
        return -1;

    // read document frequency
    this.df = buffer.getInt();

    // read term frequency
    this.tf = buffer.getInt();

    // read term idf
    this.idf = buffer.getDouble();

    // read memory offset
    this.memoryOffset = buffer.getLong();

    // read frequency offset
    this.frequencyOffset = buffer.getLong();

    // read memory size
    this.memorySize = buffer.getLong();


    return memoryOffset + ENTRY_SIZE;

    }catch(Exception e){
        e.printStackTrace();
        return -1;
    }
}

    public String getTerm() {
        return term;
    }

    public int getDf() {
        return df;
    }

    public int getTf() {
        return tf;
    }

    public double getIdf() {
        return idf;
    }

    public long getMemoryOffset() {
        return memoryOffset;
    }

    public long getMemorySize() {
        return memorySize;
    }

    public long getFrequencyOffset() {
        return frequencyOffset;
    }

}
