package org.broadinstitute.sting.gatk.walkers.annotator;

import org.broad.tribble.util.variantcontext.Genotype;
import org.broad.tribble.util.variantcontext.GenotypeLikelihoods;
import org.broad.tribble.util.variantcontext.VariantContext;
import org.broad.tribble.vcf.VCFConstants;
import org.broad.tribble.vcf.VCFHeaderLineType;
import org.broad.tribble.vcf.VCFInfoHeaderLine;
import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.contexts.StratifiedAlignmentContext;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.gatk.walkers.annotator.interfaces.InfoFieldAnnotation;
import org.broadinstitute.sting.gatk.walkers.annotator.interfaces.StandardAnnotation;
import org.broadinstitute.sting.utils.MathUtils;
import org.broadinstitute.sting.utils.Utils;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;


public class QualByDepth implements InfoFieldAnnotation, StandardAnnotation {

    public Map<String, Object> annotate(RefMetaDataTracker tracker, ReferenceContext ref, Map<String, StratifiedAlignmentContext> stratifiedContexts, VariantContext vc) {
        if ( stratifiedContexts.size() == 0 )
            return null;

        final Map<String, Genotype> genotypes = vc.getGenotypes();
        if ( genotypes == null || genotypes.size() == 0 )
            return null;

        double qual = 0.0;
        int depth = 0;

        for ( Map.Entry<String, Genotype> genotype : genotypes.entrySet() ) {

            // we care only about variant calls with likelihoods
            if ( genotype.getValue().isHomRef() )
                continue;

            StratifiedAlignmentContext context = stratifiedContexts.get(genotype.getKey());
            if ( context == null )
                continue;

            depth += context.getContext(StratifiedAlignmentContext.StratifiedContextType.COMPLETE).size();

            if ( genotype.getValue().hasLikelihoods() ) {
                GenotypeLikelihoods GLs = genotype.getValue().getLikelihoods();
                double[] likelihoods = GLs.getAsVector();
                if ( GLs.getKey() == VCFConstants.PHRED_GENOTYPE_LIKELIHOODS_KEY ) {
                    for (int i = 0; i < likelihoods.length; i++)
                        likelihoods[i] /= -10.0;
                }

                qual += 10.0 * getQual(likelihoods);
            }
        }

        if ( depth == 0 )
            return null;

        if ( qual == 0.0 )
            qual = 10.0 * vc.getNegLog10PError();

        double QbyD = qual / (double)depth;
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(getKeyNames().get(0), String.format("%.2f", QbyD));
        return map;
    }

    public List<String> getKeyNames() { return Arrays.asList("QD"); }

    public List<VCFInfoHeaderLine> getDescriptions() { return Arrays.asList(new VCFInfoHeaderLine(getKeyNames().get(0), 1, VCFHeaderLineType.Float, "Variant Confidence/Quality by Depth")); }

    private double getQual(double[] GLs) {

        // normalize so that we don't have precision issues
        double[] adjustedLikelihoods = new double[GLs.length];
        double maxValue = Utils.findMaxEntry(GLs);
        for (int i = 0; i < GLs.length; i++)
            adjustedLikelihoods[i] = GLs[i] - maxValue;

        // AB + BB (in real space)
        double variantWeight = Math.pow(10, adjustedLikelihoods[1]) + Math.pow(10, adjustedLikelihoods[2]);
        // (AB + BB) / AA (in log space)
        return Math.log10(variantWeight) - adjustedLikelihoods[0];
    }
 }