package net.javacrumbs.completionstage;

import java.util.concurrent.Executor;

public class SimpleCompletionStageFix<T> extends SimpleCompletionStage<T> {

    public SimpleCompletionStageFix(Executor defaultExecutor) {
        super(defaultExecutor);
    }

}
