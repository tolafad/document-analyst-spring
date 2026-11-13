package com.tola.analyst.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutcomeFailureTest {

	@Test
	void detailLongerThan500IsCut() {
		var failure = new Outcome.Failure(Stage.GEMINI, "gemini_http_400", "x".repeat(600));

		assertThat(failure.detail()).hasSize(Outcome.Failure.MAX_DETAIL);
	}

	@Test
	void detailOf500IsKept() {
		var failure = new Outcome.Failure(Stage.GEMINI, "gemini_http_400", "x".repeat(500));

		assertThat(failure.detail()).hasSize(Outcome.Failure.MAX_DETAIL);
	}

	@Test
	void detailIsFlattenedToOneLine() {
		var failure = new Outcome.Failure(Stage.EXTRACT, "unreadable_pdf", "line one\n\n  line   two\t");

		assertThat(failure.detail()).isEqualTo("line one line two");
	}

	@Test
	void nullDetailBecomesNone() {
		var failure = new Outcome.Failure(Stage.FILE_CHECK, "empty_file", null);

		assertThat(failure.detail()).isEqualTo("none");
	}

	@Test
	void blankDetailBecomesNone() {
		var failure = new Outcome.Failure(Stage.FILE_CHECK, "empty_file", "");

		assertThat(failure.detail()).isEqualTo("none");
	}

	@Test
	void blankReasonIsRejected() {
		assertThatThrownBy(() -> new Outcome.Failure(Stage.GEMINI, " ", "detail"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
	}

	@Test
	void nullStageIsRejected() {
		assertThatThrownBy(() -> new Outcome.Failure(null, "empty_file", "detail"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stage");
	}

	@Test
	void completedStepsAreUnmodifiable() {
		var failure = new Outcome.Failure(Stage.SHEET, "sheet_node_error", "quota", List.of("alert sent"));

		assertThatThrownBy(() -> failure.completedSteps().add("x")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void nullCompletedStepsBecomeEmpty() {
		var failure = new Outcome.Failure(Stage.EXTRACT, "unreadable_pdf", "bad xref", null);

		assertThat(failure.completedSteps()).isEmpty();
	}

	@Test
	void failuresWithSameValuesAreEqual() {
		var a = new Outcome.Failure(Stage.FILE_CHECK, "empty_file", "0 bytes");
		var b = new Outcome.Failure(Stage.FILE_CHECK, "empty_file", "0 bytes");

		assertThat(a).isEqualTo(b);
		assertThat(a).hasSameHashCodeAs(b);
	}
	
	@Test
	void completedStepsCannotBeChangedLater() {
	    List<String> steps = new ArrayList<>();
	    steps.add("sheet row 2 written");

	    var failure = new Outcome.Failure(Stage.NOTIFY, "notify_node_error", "smtp down", steps);
	    steps.add("added after the failure was built");      // change the caller's list

	    assertThat(failure.completedSteps()).containsExactly("sheet row 2 written");
	}
}