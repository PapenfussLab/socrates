package au.edu.wehi.socrates;

import java.awt.SystemColor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * 
 */

/**
 * @author jibsch
 * @author hsu
 *
 * Created on Nov 7, 2012
 */
public class SOCRATES {
	
	static String stripPathAndExtension (String str) {
        // Handle null case specially.

        if (str == null) return null;
        
        //Get position of last '/'
        int pos1 = str.lastIndexOf("/");

        // Get position of last '.'.
        int pos2 = str.lastIndexOf(".");
        // If there wasn't any '.' just return the string as is.
        if (pos2 == -1) pos2 = str.length();
        // Otherwise return the string, up to the dot.

        return str.substring(pos1+1, pos2);
    }
	
	public static boolean verbose = true;

	public static void main(String[] args) {
		
		//create options
		Options options = new Options();
		options.addOption("h", "help", false, "show this help message and exit");
		options.addOption( Option.builder( "l" )
				.longOpt("long-sc-len")
                .desc( "Length threshold of long soft-clip (default: 25)" )
                .hasArg()
                .argName("LONG_SC_LEN") 
                .build());
		int default_long_sc_len = 25;
		options.addOption( Option.builder( "b" )
				.longOpt("base-quality")
                .desc( "Minimum average base quality score of soft clipped sequence (default: 5)" )
                .hasArg()
                .argName("BASE_QUALITY") 
                .build());
		int default_base_quality = 5;
		options.addOption( Option.builder( "p" )
				.longOpt("percent-id")
                .desc( "Minimum alignment percent identity to reference (default: 95)" )
                .hasArg()
                .argName("PERCENT_ID") 
                .build());
		int default_percent_id = 95;
		options.addOption( Option.builder( "q" )
				.longOpt("min-mapq")
                .desc( "Minimum alignments mapq (default: 5)" )
                .hasArg()
                .argName("MIN_MAPQ") 
                .build());
		int default_mapq = 5;
		options.addOption( Option.builder( "t" )
				.longOpt("threads")
                .desc( "Number of threads (default: 1)" )
                .hasArg()
                .argName("THREADS") 
                .type(Integer.class)
                .build());
		int default_threads = 1;
		options.addOption( Option.builder()
				.longOpt("keep-duplicates")
                .desc( "Keep duplicate reads (default: False)" )
                .build());
		options.addOption( Option.builder( "f" )
				.longOpt("flank")
                .desc( "Size of flank for promiscuity filter (default: 50)" )
                .hasArg()
                .argName("FLANK") 
                .build());
		int default_flank = 50;
		options.addOption( Option.builder( )
				.longOpt("promiscuity")
                .desc( "Exclude cluster if more than PROMISCUITY clusters within FLANK(nt) of a breakpoint (default: 5)" )
                .hasArg()
                .argName("PROMISCUITY") 
                .build());
		int default_promiscuity = 5;
		options.addOption( Option.builder()
				.longOpt("no-short-sc-cluster")
                .desc( "Disable search for short soft clip cluster support for unpaired clusters. (default: False)" )
                .build());
		options.addOption( Option.builder( )
				.longOpt("max-support")
                .desc( "Maximum realignment support to search for short SC cluster. (default: 30)" )
                .hasArg()
                .argName("MAX_SUPPORT") 
                .build());
		int default_max_support = 30;
		options.addOption( Option.builder()
				.longOpt("ideal-only")
                .desc( "Use only proper pair 5' SC and anomalous pair 3' SC (default: False)" )
                .build());
		options.addOption( Option.builder()
				.longOpt("verbose")
                .desc( "be verbose of progress (default: False)" )
                .build());
		options.addOption( Option.builder( )
				.longOpt("normal")
                .desc( "Socrates paired breakpoint calls for normal sample (default: None)" )
                .hasArg()
                .argName("NORMAL") 
                .build());
		options.addOption( Option.builder( )
				.longOpt("repeatmask")
                .desc( "UCSC repeat masker track file in Tabix form (default: None)" )
                .hasArg()
                .argName("REPEATMASK") 
                .build());
		
		
		// automatically generate the help statement
		if(args.length < 2){
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp( "Socrates all [Options] <bowtie2_index> <input_bam>\n\n"
				+"positional arguments:\n"
				+"\tbowtie2_index\t\tThe Bowtie2 index to the reference genome (the chromosome names have to be identical to those in the BAM file.\n"
				+"\tinput_bam\t\tThe input BAM file that Socrates runs on\n\noptions:" , options );
			System.exit(0);
		}
		
		// create the command line parser
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		try {
		    line = parser.parse( options, args );
		    verbose = line.hasOption("verbose");
		}
		catch( ParseException exp ) {
		    System.out.println( "Unexpected exception while parsing arguments:" + exp.getMessage() );
		    System.exit(0);
		}
		
		/* 
		 * Parse all arguments
		 */
		int threads = line.hasOption("threads")? Integer.parseInt(line.getOptionValue("threads") ): default_threads;
		int long_sc_len = line.hasOption("long-sc-len")? Integer.parseInt(line.getOptionValue("long-sc-len")) : default_long_sc_len;
		int base_qual = line.hasOption("b")? Integer.parseInt(line.getOptionValue("b")) : default_base_quality;
		int mapq = line.hasOption("q")? Integer.parseInt(line.getOptionValue("q")) : default_mapq;
		int percent_id = line.hasOption("p")? Integer.parseInt(line.getOptionValue("p")) : default_percent_id;
		int max_support = line.hasOption("max-support")? Integer.parseInt(line.getOptionValue("max-support")) : default_max_support;
		int promiscuity = line.hasOption("promiscuity")? Integer.parseInt(line.getOptionValue("promiscuity")) : default_promiscuity;
		int flank = line.hasOption("flank")? Integer.parseInt(line.getOptionValue("flank")) : default_flank;
		boolean keep_duplicates = line.hasOption("keep-duplicates");
		boolean no_short_scs = line.hasOption("no-short-sc-cluster");
		boolean ideal_only = line.hasOption("ideal-only");

		String index = line.getArgs()[0];
		String bam = line.getArgs()[1];
		
		System.err.println("Starting up...");
		
		/*
		 * Stratify the BAM file as a preprocessing step
		 */
		System.err.println("\nStratify BAM file " + bam );
		System.err.println();
		BAMStratifier b = new BAMStratifier(
				bam, 
 			    threads, 
				long_sc_len, 
				base_qual,
				mapq,
				percent_id,
				keep_duplicates, 
				no_short_scs);
	    b.stratifyAll();
		
	    /*
	     * Realign the long soft clips with BT2
	     */
	    String base = stripPathAndExtension(bam);
	    String suffix = String.format("_long_sc_l%d_q%d_m%d_i%d" ,  long_sc_len, base_qual, mapq, percent_id );
	    String long_sc_filestem = base+suffix, long_sc_fastq = long_sc_filestem+".fastq.gz", long_sc_bam = long_sc_filestem+".bam",
	    		short_sc_bam = base+"_short_sc.bam", metrics = bam + ".metrics";

	    ProcessBuilder pb = new ProcessBuilder("bowtie2",
	    		"-p",
	    		String.valueOf(threads),
	    		"--local",
	    		"-x",
	    		index,
	    		"-U",
	    		long_sc_fastq);
	    System.err.println("Realignment of long soft-clips: Running Bowtie2.");
		System.err.println("Piping Bowtie2 output into RealignmentBAM...");
			Process p;
			try {
				p = pb.start();

				RealignmentBAM.makeRealignmentBAM("-", long_sc_bam, p);

				InputStream es = p.getErrorStream();
				InputStreamReader isr = new InputStreamReader(es);
				BufferedReader br = new BufferedReader(isr);
				String out;
				while ((out = br.readLine()) != null) {
					System.out.println(out);
				}

				p.waitFor();
			} catch (IOException | InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
	    
	    /*
	     * Cluster the re-aligned soft clips.
	     */
	    System.err.println( "\nClustering re-alignments. Procducing output in results_Socrates_[paired/unpaired]..." );
	    RealignmentClustering r = new RealignmentClustering(
				long_sc_bam, 
				short_sc_bam, 
				metrics, 
				threads /*threads*/, 
				mapq /*minMapq*/, 
				max_support /*maxSupport*/, 
				long_sc_len,
				percent_id,
				promiscuity,
				flank,
				ideal_only /*idealOnly*/,
				no_short_scs /*use short_sc_cluster*/);
	    try {
			r.clusterRealignedSC(null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    
	    System.err.println("Socrates has run completely.");
	    
	}
	
}
