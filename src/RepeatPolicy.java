import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/** Rejection policy nella quale si tenta di reinserire il task più volte aspettando piccoli intervalli di tempo tra un
 *  tentativo e l'altro. Se non si riesce a inserire il task per nTimes volte, allora lo si esegue manualmente.
 *
 */
public class RepeatPolicy implements RejectedExecutionHandler {
    private final int nTimes;
    private final long msToWait;

    public RepeatPolicy(int nTimes, long msToWait) {
        this.nTimes = nTimes;
        this.msToWait = msToWait;
    }
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        boolean accepted = false;
        int i = 0;

        while (i < nTimes && !accepted) {
            try {
                executor.submit(r);
                accepted = true;
            }
            catch (RejectedExecutionException e) {
                i++;
                if (i == 5)
                    r.run();
                else {
                    try {
                        Thread.sleep(msToWait);
                    } catch (InterruptedException ex) {
                        r.run();
                    }
                }
            }
        }
    }
}
