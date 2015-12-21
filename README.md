# socrates

Authors: Jan Schröder, Arthur Hsu, Anthony T Papenfuss 
Date: 11.09.2015

------------
Release 1.13
------------

Releases from 1.1 onward depend on java 1.8. If Java 1.8 is not an option, please use the old release on https://github.com/jibsch/socrates



INSTALLATION:

download the current release (socrates-1.11-jar-with-dependencies.jar) -- it contains all necessary libraries.

USAGE:

run the jar file: *java -Xmx10g -jar socrates-1.11-jar-with-dependencies.jar*
(Xmx10g specifies the maximum memory usage for the java virtual machine, and has to be adjusted as necessary)
this command runs the full Socrates pipeline. Each stage of the pipeline can be invoked individually.
To call a Socrates subcommand, type: *java -Xmx10g -cp socrates-1.1-jar-with-dependencies.jar au.edu.wehi.socrates.[command] *
(which can be one of BamStratifier, RealignmentBAM, RealignmentClustering, or AnnotatePairedClusters -- more details below).

While default values (shown in the help messages) have worked satisfactorily in our simulated and real
cancer genome sequencing datasets, users should set program parameters in the
driver scripts appropriately for their own data. Full lists of program
parameters are provided in the following sections, together with a discussion
on the impact of changing them where applicable.


1.1. Preprocess BAM File 

Stratifies the original BAM file into long and short soft-clips.

usage: command=*BAMStratifier* [options] alignment bam

-b,	--base-quality <score>	Minimum average base quality score of soft clipped sequence  [default: 5] 

-h,	--help 			print this message 

-k,	--keep-duplicate	keep duplicate reads [default: false]

-l,	--long-sc-len <length>	Length threshold of long soft-clip [default: 25 (bp)]

-p,	--percent-id <pid> 	Minimum alignment percent identity to reference [default: 95 (%)]

-q,	--min-mapq <mapq> 	Minimum alignments mapq [default: 5]

-t,	--threads <threads> 	Number of threads to use [default: 1]

-v,	--verbose		be verbose of progress

    
Minimum base quality option:
 
A reasonable threshold helps removing low quality
soft clips that could lead to erroneous breakpoint calls.

Long soft clip length: 

Studies have shown that longer the sequences the more
likely they can be uniquely placed in a genome. In an early study, it is
demonstrated that while percentage of unique mapping improves with increasing
read length, the rate of gain diminishes past 25nt ( 80% at 25nt and 90% at
40nt). If value for this parameter is too low, many non-unique soft clips will
be produced and impact on system requirement, processing time and reliability
of results downstream. On the other hand, too high the value results in low
number of long soft clips and hence risk of missing breakpoints.
Percent identity: We often observe higher-than-expected base mismatch rate for
reads in satellite, centromeric and telomeric regions where correctness of
alignments can be contentious. Minimum percent identity threshold, which is
equivalent to maximum allowable mismatch rate, can greatly reduce these
erroneous alignments.

Minimum mapping quality:
 
Higher mapping quality, while may not guarantee
unique alignment, is sufficient to exclude multi-mapping anchor alignments
from further analysis for Bowtie2 and BWA aligned reads.


1.2. Process the re-alignment BAM file

usage: Command=*RealignmentBAM* [options] input_bam output_bam

input_bam 	Re-aligned soft clip BAM file. Use “-” to accept input from stdin

output_bam 	Output re-alignment BAM with anchor info merged

anchor_info	Anchor info file produced by BAMStratifier


This program merges soft clip re-alignment BAM file with anchor alignment
information. The program has a built-in sorting mechanism and therefore can take
unsorted, raw re-alignment output from the aligner. While the program accepts
input BAM file from standard input channel, this requires more system memory
for buffering.

To run this stage together with an aligner, pipe the SAM output straight into 
the program. For example: 
*Bowtie2 --local -x INDEX -U SCs | java -jar socrates au.edu.wehi.socrates.RealignmentBAM - OUT*


1.3. Predict rearrangements

usage: command=*RealignmentClustering* [options] realigned_sc_bam short_sc_bam metrics_file

-f,	--flank <flank> 		Size of flank for promiscuity filter [default: 50 (bp)]

-h,	--help 				print this message 

-i,	--ideal-only 			Use only proper pair 5’ SC and anomalous pair 3’ SC [default: false]

-l,	--long-sc-len <length> 		Length threshold of long soft-clip [default: 25 (bp)]

-m,	--promiscuity <threshold>	Exclude cluster if more than [promiscuity] clusters within [flank]bp of a break

-p,	--percent-id <pid>		Minimum realignment percent identity to reference [default: 95 (%)]

-q,	--min-mapq <mapq> 		Minimum realignments mapq [default: 2]

-c,	--short-sc-cluster 		Search for short soft clip cluster support for unpaired clusters 

-s,	--max-support <support> 	Maximum realignment support to search for short SC cluster [default: 30]

-t,	--threads <threads> 		Number of threads to use [default: 3]

-v,	--verbose			be verbose of progress
     
The rearrangement predictor is the main part of the algorithm. It clusters the 
split reads, and then pairs clusters to form the output. 
There are two files generated: the paired and unpaired outputs. The paired output
contains the best results, while the unpaired contains any soft-clips that have 
realigned anywhere else in the process re-alignments stage of the algorithm.
The unpaired results are included for completeness as they can contain some
useful information, but overall this output is comprised of false positives 
due to mapping errors and other artifacts. 
The paired output contains various columns of information that describe the 
location of the break point and the level of support:
1. C1_realign - this genomic locus describes the position of the realigned 
soft-clips of cluster 1. The position is a consensus if the realignments are
not exactly the same for all soft-clips. The locus is that of the first soft-
clipped base, so the one immediately next to the breakpoint.
2. C1_realign_dir - this field takes either "+" or "-" and indicates whether 
the realigned soft-clips map upstream of C1_realign (+) or downstream (-) --
with respect to the reference genome.
3. C1_realign_consensus - a consensus sequence made of soft-clips in cluster 1.
An asterisk optionally marks the position of the breakpoint within the 
consensus (if the position was unanimous). 
4. C1_anchor - a second locus describing the anchor region of the cluster. 
The anchor is defined by the reads that were mapped in the initial alignments
and which soft-clips formed the earlier columns. The position is the consensus
of positions before the first soft-clipped base.
5. C1_anchor_dir - analogous to above this field describes whether the anchor
region is upstream ("+") or downstream ("-") of the breakpoint in the reference.
6. C1_anchor_consensus - the consensus sequence of the anchor reads.
7. C1_long_support - this number counts the number of "long" soft-clips (as
specified during the run of Socrates) that support the cluster C1. This number is
at least 1, as there would not be a cluster without a realigned soft-clip.
8. C1_long_support_bases - the number of nucleotides in the long support of 
the preceding column counted and reported in this column.
9. C1_short_support - similarly, the short support is counted in number...
10. C1_short_support_bases - ... and nucleotides.
11. C1_short_support_max_len - the length of the longest SC in this support group.
12. C1_avg_realign_mapq - this last column for C1 summarizes the average mapping
quality of the anchor reads. It can be a helpful filter criteria and a minimum
value can be specified at the launch of Socrates.
13-24. C2 columns - all columns described above are repeated for C2. There is 
only one noticeable difference: C2 can be a cluster formed without realigned 
soft-clips (see "short SC cluster" below), which leads to empty consensus 
sequences (there are double-tabs in the output, which can be quite nasty to
deal with. Apologies!) and long-support values set to 0.
Finally, the column labeled "BP_condition" describes the nature of the fusion 
event. It can take any of five values:
1. Blunt-end joining: the most straight forward case of a clean join (none
of the below).
2. Micro-homology: Xbp homology found! (XXX): the two joined regions are 
identical for X bases across the break. Therefore the true location of the 
breakpoint is only known within those boundaries.
3. Inserted sequence: XXX: There is a short bit of sequence inserted in 
between the two loci of the fusion. The sequence is either untemplated or 
from somewhere else in the genome (but too short to map).
4. unequal distances of realigned breakpoint to anchor breakpoint: X v Y: 
In this case the realignment and anchor loci of the two paired clusters do 
not support the exact same coordinate for a fusion (|X-Y| indicates the 
difference). This is usually due to mis-mappings.
5. Unequal inserted sequence: XXX v Y: an insert occurs as above, but 
Socrates was unable to determine the exact sequence. One of the values 
should contain the correct sequence.
6. short SC cluster: In most experiments the most prevalent type, yet the
least trustworthy. Only one side of the breakpoint is supported by realigned
split reads, the other by short bits of soft-clipped sequence only. This sort
of cluster pairing makes Socrates very sensitive, but introduces false 
positives.
  
1.4. Annotating rearrangements

usage: command=*AnnotatePairedClusters* [options] socrates_paired_cluster_output

-n,	--normal <normal>	Socrates paired breakpoint calls for normal sample 

-r,	--repeatmask <file>	UCSC repeat masker track file in BED format, Tabix indexed.

1.5. Structural variant types
The current version of Socrates does not support typing of variants as other
tools do. The reasoning is that typing events can interfere with the interpretation 
of results as they might be too suggestive. For example, insertions
(of novel sequence) and deletions have the same breakpoint signature. This 
signature is commonly referred to as the deletion type. The deletion type is
also involved in other, more complex rearrangements. We therefore have so far
refrained from annotating these types. 
However, for the sake of completeness, here are the type signatures relating to 
Socrates breakpoints (let us assume that C1 realign and C1 anchor are on the same 
chromosome and C1 realign pos < C1 anchor pos):
C1_realign_dir + & C1_anchor_dir -: DELETION TYPE
C1_realign_dir - & C1_anchor_dir +: TANDEM DUPLICATION TYPE
C1_realign_dir + & C1_anchor_dir +: INVERSION TYPE (I)
C1_realign_dir - & C1_anchor_dir -: INVERSION TYPE (II)
If C1 and C2 are on different chromosomes, the consensus for types is not as 
clearly defined as those types above. 
