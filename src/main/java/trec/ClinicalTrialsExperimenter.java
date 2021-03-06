package trec;


import clinicaltrial.ClinicalTrial;
import clinicaltrial.TrecConfig;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import trec.experiment.Experiment;
import trec.experiment.ExperimentsBuilder;
import trec.search.ElasticClientFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringEscapeUtils.*;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class ClinicalTrialsExperimenter {
	public static void main(String[] args) {
//		try {
//			indexAllClinicalTrials("/Users/jian-0526/Desktop/TREC/clinicaltrials_xml");
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		final File cancerSynonymsTemplate = new File(
				ClinicalTrialsExperimenter.class.getResource("/templates/clinical_trials/cancer-synonyms-ct.json").getFile());
		final int year = 2017;

		ExperimentsBuilder builder = new ExperimentsBuilder();

		builder.newExperiment().withYear(year).withTemplate(cancerSynonymsTemplate).withWordRemoval();

		Experiment experiment = builder.build();
		experiment.start();
	}

	static long indexAllClinicalTrials(String dataFolderWithFiles) throws Exception {
		System.out.println("STARTING INDEXING");

		long startTime = System.currentTimeMillis();

		BulkProcessor bulkProcessor = buildBuildProcessor();
		Files.walk(Paths.get(dataFolderWithFiles))
				.filter(Files::isRegularFile)
				.forEach(file -> {
					String fileName = file.toString();
					if (fileName.endsWith(".xml")) {
						ClinicalTrial trial = getClinicalTrialFromFile(file.toString());
						System.out.println("ADDING: " + trial.id);

						try {
							bulkProcessor.add(new IndexRequest(TrecConfig.ELASTIC_CT_INDEX, TrecConfig.ELASTIC_CT_TYPE, trial.id)
									.source(buildJson(trial)));
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
				});

		bulkProcessor.awaitClose(10, TimeUnit.MINUTES);

		long indexingDuration = (System.currentTimeMillis() - startTime);

		System.out.println("INDEXING TIME BULK: " + indexingDuration/1000 + " secs");

		return indexingDuration;
	}

	private static BulkProcessor buildBuildProcessor() {
		Client client = ElasticClientFactory.getClient();

		return BulkProcessor.builder(
				client,
				new BulkProcessor.Listener() {
					@Override
					public void beforeBulk(long executionId,
										   BulkRequest request) {

					}

					@Override
					public void afterBulk(long executionId,
										  BulkRequest request,
										  BulkResponse response) {
						if (response.hasFailures()) {
							throw new RuntimeException(response.buildFailureMessage());
						}
					}

					@Override
					public void afterBulk(long executionId,
										  BulkRequest request,
										  Throwable failure) {
						throw new RuntimeException(failure);
					}
				}).build();
	}

	private static XContentBuilder buildJson(ClinicalTrial trial) throws IOException {
		return jsonBuilder()
				.startObject()
				.field("id", trial.id)
				.field("brief_title", escapeJson(trial.brief_title))
				.field("official_title", escapeJson(trial.official_title))
				.field("summary", escapeJson(trial.summary))
				.field("description", escapeJson(trial.description))
				.field("studyType", trial.studyType)
				.field("interventionModel", trial.interventionModel)
				.field("primary_purpose", trial.primaryPurpose)
				.field("outcomeMeasures", trial.outcomeMeasures)
				.field("outcomeDescriptions", trial.outcomeDescriptions)
				.field("conditions", trial.conditions)
				.field("interventionTypes", trial.interventionTypes)
				.field("interventionNames", trial.interventionNames)
				.field("armGroupDescriptions", trial.armGroupDescriptions)
				.field("sex", trial.sex)
				.field("minimum_age", trial.minAge)
				.field("maximum_age", trial.maxAge)
				.field("inclusion", escapeJson(trial.inclusion))
				.field("exclusion", escapeJson(trial.exclusion))
				.field("keywords", trial.keywords)
				.field("meshTags", trial.meshTags)
				.endObject();
	}

	public static ClinicalTrial getClinicalTrialFromFile(String xmlTrialFileName) {

		return(ClinicalTrial.fromXml(xmlTrialFileName));
	}

}
