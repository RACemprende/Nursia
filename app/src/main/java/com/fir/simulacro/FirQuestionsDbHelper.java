package com.fir.simulacro;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FirQuestionsDbHelper {
    private final AppDatabaseHelper appDatabaseHelper;

    public FirQuestionsDbHelper(Context context) {
        this.appDatabaseHelper = new AppDatabaseHelper(context);
    }

    public List<QuestionRecord> loadAllQuestions() throws IOException {
        return mapQuestions(appDatabaseHelper.loadAllQuestions());
    }

    public List<QuestionRecord> loadQuestionsByStates(List<String> states) throws IOException {
        return mapQuestions(appDatabaseHelper.loadQuestionsByStates(states));
    }

    private List<QuestionRecord> mapQuestions(List<AppDatabaseHelper.QuestionRecord> sourceQuestions) {
        List<QuestionRecord> questions = new ArrayList<>();
        for (AppDatabaseHelper.QuestionRecord question : sourceQuestions) {
            questions.add(new QuestionRecord(
                    question.year,
                    question.questionNumber,
                    question.statement,
                    question.options,
                    question.correctIndex
            ));
        }
        return questions;
    }

    public static class QuestionRecord {
        public final String year;
        public final String questionNumber;
        public final String statement;
        public final List<String> options;
        public final int correctIndex;

        public QuestionRecord(String year, String questionNumber, String statement, List<String> options, int correctIndex) {
            this.year = year;
            this.questionNumber = questionNumber;
            this.statement = statement;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }
}
