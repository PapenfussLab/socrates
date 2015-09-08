package au.edu.wehi.socrates.util;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;

import java.util.Arrays;
import java.util.Vector;


/**
 * @author jibsch
 * @author hsu
 *
 * Created on Jan 16, 2013
 */
public class SAMRecordSummary {
	private byte[] headClipSeq=null, tailClipSeq=null;
	private byte[] headClipQual=null, tailClipQual=null;
	private int headClipPos=-1, tailClipPos=-1;
	
	private byte[] alignedSeq=null;
	private byte[] alignedQual=null;
	private float alignedPercentIdentity=0f;
	private boolean isReadReversed=false;
	
//	public String id;
	
	public SAMRecordSummary(SAMRecord aln) {
		java.util.List<CigarElement> cigar = aln.getCigar().getCigarElements();
		byte[] sequence = aln.getReadBases();
		byte[] qual = aln.getBaseQualities();
		
		extractSoftClipInfo(cigar, sequence, qual, aln);
		
		extractAlignedSeqInfo(cigar, sequence, qual, aln);
	}
	
	public static boolean isAlignmentSoftClipped(SAMRecord aln) {
		java.util.List<CigarElement> cigar = aln.getCigar().getCigarElements();
		return (cigar.get(0).getOperator()==CigarOperator.SOFT_CLIP || cigar.get(cigar.size()-1).getOperator()==CigarOperator.SOFT_CLIP);
	}
	
	private void extractSoftClipInfo(java.util.List<CigarElement> cigar, byte[] sequence, byte[] qual, SAMRecord aln) {
		// head clip info
		CigarElement firstCigar = cigar.get(0);
		if (firstCigar.getOperator() == CigarOperator.SOFT_CLIP) {
			// head clip, 5' reference
			headClipSeq = Arrays.copyOfRange(sequence, 0, firstCigar.getLength());  // clip sequence with RIGHT MOST base adjacent to breakpoint
			headClipQual = Arrays.copyOfRange(qual, 0, firstCigar.getLength());
			headClipPos = aln.getAlignmentStart();
		} else {
			headClipSeq = new byte[0];
			headClipQual = new byte[0];
			headClipPos = -1;
		}
		
		// tail clip info
		CigarElement lastCigar = cigar.get(cigar.size()-1);
		if (lastCigar.getOperator() == CigarOperator.SOFT_CLIP) {
			// tail clip, 3' reference
			tailClipSeq = Arrays.copyOfRange(sequence, sequence.length-lastCigar.getLength(), sequence.length);
			tailClipQual = Arrays.copyOfRange(qual, sequence.length-lastCigar.getLength(), sequence.length);
			Utilities.reverseComplement(tailClipSeq); // make clip sequence with RIGHT MOST base adjacent to breakpoint
			Utilities.reverse(tailClipQual);
			tailClipPos = aln.getAlignmentEnd();
		} else {
			tailClipSeq = new byte[0];
			tailClipQual = new byte[0];
			tailClipPos = -1;
		}
	}

	private void extractAlignedSeqInfo(java.util.List<CigarElement> cigar, byte[] sequence, byte[] qual, SAMRecord aln) {
		// read info
		isReadReversed = aln.getReadNegativeStrandFlag();
		
		// aligned seq info
		Vector<Byte> as = new Vector<Byte>();
		Vector<Byte> aq = new Vector<Byte>();
		int pos = 0;
		for (CigarElement op : cigar) {
			switch ( CigarOperator.enumToCharacter( op.getOperator() ) ) {
			case 'H':
				break;
			case 'S':
				pos += op.getLength();
				break;
			case 'D':
				for (int n=0; n<op.getLength(); n++) {
					as.add( new Byte((byte)'N') );
					aq.add( new Byte((byte)0) );
				}
				break;
			default:
				try {
					for (int n=0; n<op.getLength(); n++) {
						as.add( sequence[pos] );
						aq.add( qual[pos] );
						pos++;
					}
				} catch (Exception e) {
					System.out.println("\nsequence: " + sequence.length);					
					for (CigarElement optmp : cigar)
						System.out.print((char)CigarOperator.enumToCharacter(optmp.getOperator()));
					System.out.println("\npos: " + pos);
					System.out.println(new String(aln.getReadBases()));					
					e.printStackTrace(System.out);
				}
			}
		}
		
		alignedSeq = new byte[as.size()];
		alignedQual = new byte[as.size()];
		for (int i=0; i<as.size(); i++) {
			alignedSeq[i] = as.get(i).byteValue();
			alignedQual[i] = aq.get(i).byteValue();
		}
		alignedPercentIdentity = calcAlignedPercentIdentity(aln);
	}
	
	private float calcAlignedPercentIdentity(SAMRecord aln) {
		Object nm = aln.getAttribute("NM");
		if (nm!=null) {
			// NM is edit distance, containing INDELs, add INDEL count back to give correct mismatch count.
			//
			int indels = 0;
			for (CigarElement c : aln.getCigar().getCigarElements()) {
				CigarOperator op = c.getOperator(); 
				if (op == CigarOperator.INSERTION || op == CigarOperator.DELETION) indels += c.getLength();
			}
			return (float)(alignedSeq.length-((Integer)nm).intValue()-indels)/(float)(alignedSeq.length);
		}
		
		// alternatively, generate mismatches from MD tag.
    	Object md = aln.getAttribute("MD");
    	if (md==null) {
    		return -1;
    	}
    	
    	String MD = (String)md;
        int mismatch = 0;
        int match = 0;
        int tok = 0;
        StringBuilder b = new StringBuilder();
        for (int i=0; i<MD.length(); i++) {
            char c = MD.charAt(i);
            if (tok%2==0 && !Character.isDigit(c)) {
                tok++;
                match += Integer.parseInt( b.toString() );
                b = new StringBuilder();
            }
            if (tok%2==1 && Character.isDigit(c)) {
                tok++;
                mismatch += b.charAt(0)=='^' ? b.length()-1 : b.length();
                b = new StringBuilder();
            }
            b.append( c );
        }
        if (tok%2==0) match += Integer.parseInt( b.toString() );
        if (tok%2==1) mismatch += b.charAt(0)=='^' ? b.length()-1 : b.length();
        
        return (float)(match)/(match+mismatch);
    }
	
	public float getAlignedPercentIdentity() { return alignedPercentIdentity; }
	
	public boolean getIsReadReversed() { return isReadReversed; }
	
	public boolean isClipped() { return (headClipSeq.length!=0 || tailClipSeq.length!=0); }
	
	public boolean isHeadClipped() { return (headClipSeq.length!=0); }
	
	public int getHeadClipPos() { return headClipPos; }
	
	public boolean isTailClipped() { return (tailClipSeq.length!=0); }
	
	public int getTailClipPos() { return tailClipPos; }
	
	/**
	 * Left most base is adjacent to breakpoint
	 * @return
	 */
	public byte[] getHeadClipAlignedSequence() {
		return alignedSeq;
	}
	
	/**
	 * Left most base is adjacent to breakpoint
	 * @return
	 */
	public byte[] getTailClipAlignedSequence() {
		return Utilities.getReversedComplementArray(alignedSeq);
	}
	
	/**
	 * RIGHT most base is adjacent to breakpoint
	 * @return
	 */
	public byte[] getHeadClipSequence() { 
		return headClipSeq;
	}

	/**
	 * RIGHT most base is adjacent to breakpoint
	 * @return
	 */
	public byte[] getTailClipSequence() { 
		return tailClipSeq;
	}
	
	
	public byte[] getHeadClipQuality() { 
		return headClipQual;
	}
	
	public byte[] getTailClipQuality() { 
		return tailClipQual;
	}
	
	public float getAvgHeadClipQuality() { return getAverageQuality(headClipQual); }
	public float getAvgTailClipQuality() { return getAverageQuality(tailClipQual); }
	
	private float getAverageQuality(byte[] qual) {
		if (qual==null) return -1f;
		
		float sum = 0f;
		for (byte b : qual) sum += (float)b;
		return sum/(float)qual.length;
	}
}
