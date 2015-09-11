package au.edu.wehi.socrates;

import java.io.File;

import htsjdk.samtools.*;
import htsjdk.samtools.SAMFileHeader.SortOrder;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SortingCollection;
import au.edu.wehi.socrates.util.ProgressVerbose;
import au.edu.wehi.socrates.util.SAMFileInfo;

/**
 * @author jibsch
 * @author hsu
 *
 * Created on Jan 23, 2013
 */
public class RealignmentBAM {
	public RealignmentBAM() {}

	
	public static void makeRealignmentBAM(String inputBAMFilename, String outputBamFilename, Process bowtie) {
		SamReader sam;
		SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT);
		if(inputBAMFilename.equals("-")){
			SamInputResource realignInput = SamInputResource.of(bowtie.getInputStream());
			sam = samReaderFactory.open(realignInput);
		} else {
			sam = samReaderFactory.open(new File(inputBAMFilename));
		}
		
		ProgressVerbose p = new ProgressVerbose("Sorting alignments", 1000000, SOCRATES.verbose);
		long mismatchRef = 0;
		try {
			SortingCollection<SAMRecord> buffer = SortingCollection.newInstance(SAMRecord.class, new BAMRecordCodec(sam.getFileHeader()), 
																				new SAMRecordCoordinateComparator(), 500000, new File("."));
			SAMFileInfo info = new SAMFileInfo(sam);
			
			SAMRecordIterator iter = sam.iterator();
			while (iter.hasNext()) {
				SAMRecord aln = iter.next();
				
				if (aln.getNotPrimaryAlignmentFlag()) continue;
				
				p.stepProgress(" alignments added to sorting collection");
				String readName = aln.getReadName();
				String[] tokens = readName.split("&");
				assert tokens.length == 5;
				
				int mateRefId = info.getSequenceIndex(tokens[1]);
				if (mateRefId < 0) {
					mismatchRef++;
				//	System.err.println("Read: " + tokens[0]);
				//	System.err.println("\tAnchor reference sequence " + tokens[1] + " does not exist in realignment BAM file.");
				//	System.err.println("\tSkipped");
					continue;
				}
				
				aln.setReadName( tokens[0] );
				aln.setFirstOfPairFlag(true);
				aln.setProperPairFlag(false);
				aln.setReadPairedFlag(true);
				aln.setMateReferenceIndex( mateRefId );
				aln.setMateAlignmentStart( Integer.parseInt(tokens[2]) );
				aln.setMateNegativeStrandFlag( tokens[3].equals("-") );
				aln.setMateUnmappedFlag( !(tokens[4].equals("1")) );
				
				aln.setAttribute("ZS", tokens[5]);
				buffer.add(aln);
			}
			iter.close();
			p.end(" alignments added to sorting collection");
			
			ProgressVerbose p2 = new ProgressVerbose("Creating realignment BAM file", 1000000, SOCRATES.verbose);
			SAMFileWriterFactory.setDefaultCreateIndexWhileWriting(true);
			sam.getFileHeader().setSortOrder(SortOrder.coordinate);
			SAMFileWriter bam = new SAMFileWriterFactory().makeBAMWriter( sam.getFileHeader(), true, new File(outputBamFilename) );
			
			CloseableIterator<SAMRecord> iter2 = buffer.iterator();
			while (iter2.hasNext()) {
				SAMRecord aln = iter2.next();
				bam.addAlignment(aln);
				p2.stepProgress(" alignments written");
			}
			p2.end(" alignments written");
			sam.close();
			bam.close();

			if (mismatchRef > 0) {
				System.err.println("WARNING: " + mismatchRef + " alignments with no matching reference chromosome name");
				System.err.println("in realignment BAM's sequence dictionary. Please ensure the same alignment indexes");
				System.err.println("are used for both raw read alignment and soft-clip realignment.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	class Info {
		public int chromIdx, pos;
		public char orientation;
		public byte[] seq;
		public boolean ideal;
		
		public Info() {};
		
		public Info(int chromIdx, int pos, char orientation, byte[] seq, char ideal) {
			this.chromIdx = chromIdx;
			this.pos = pos;
			this.orientation = orientation;
			this.seq = seq;
			this.ideal = (ideal=='1');
		}
	}

    public static void printHelp() {
        System.err.println("usage: RealignmentBAM input_bam output_bam");
        System.err.println(" set input_bam to \"-\" to accept input from stdin");
    }

	
	public static void main(String[] args) {
		if (args.length==2) {
            SOCRATES.verbose = false;
            System.err.println("\nAdd anchor information into re-alignment BAM file");
            System.err.println("  input BAM file:\t" + args[0] );
            System.err.println("  output BAM file:\t" + args[1] );
			makeRealignmentBAM(args[0], args[1], null);
		}
		else {
			for (int i=0; i<args.length; i++)
				System.err.println(args[i]);
            printHelp();
            System.exit(1);
		}
	}
}

