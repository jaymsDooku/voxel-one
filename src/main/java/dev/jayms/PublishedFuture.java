package dev.jayms;

import java.util.concurrent.*;

/** One-shot CPU task. A ready result never waits for FutureTask's completion metadata. */
public class PublishedFuture<T> extends FutureTask<T> {
    private final CompletableFuture<T> published;

    public PublishedFuture(Callable<T> action){this(action,new CompletableFuture<>());}
    private PublishedFuture(Callable<T> action,CompletableFuture<T> result){
        super(()->{
            try {
                T value=action.call();result.complete(value);return value;
            }catch(Throwable failure){
                // Preserve the original cause even when the task itself throws CancellationException.
                result.completeExceptionally(new CompletionException(failure));
                if(failure instanceof Exception exception)throw exception;
                if(failure instanceof Error error)throw error;
                throw new AssertionError(failure);
            }
        });
        published=result;
    }
    @Override public boolean cancel(boolean interrupt){
        if(published.isDone()||!published.completeExceptionally(new CancellationException()))return false;
        super.cancel(interrupt);return true;
    }
    @Override public boolean isDone(){return published.isDone();}
    @Override public boolean isCancelled(){return published.isCancelled();}
    @Override public T get()throws InterruptedException,ExecutionException{return published.get();}
    @Override public T get(long timeout,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{return published.get(timeout,unit);}
}
