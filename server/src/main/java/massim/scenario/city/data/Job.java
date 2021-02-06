package massim.scenario.city.data;

import massim.protocol.scenario.city.data.ItemAmountData;
import massim.protocol.scenario.city.data.JobData;
import massim.scenario.city.data.facilities.Storage;
import massim.util.Log;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * A job in the City scenario.
 */
public class Job {

    private final static AtomicInteger counter = new AtomicInteger();

    JobStatus status = JobStatus.FUTURE;
    private String name = "";
    private Storage storage;
    private int reward;
    private int beginStep;
    private int endStep;
    private String poster;

    private ItemBox requiredItems;
    private Map<String, ItemBox> deliveredItems = new HashMap<>();

    /**
     * Constructor.
     * @param reward the reward of the job
     * @param storage the storage associated with this job
     * @param begin in which step to start the job
     * @param end the last step of the job
     * @param poster the origin of the job (the system or any team)
     */
    public Job(int reward, Storage storage, int begin, int end, ItemBox requiredItems, String poster){
        this.reward = reward;
        this.storage = storage;
        this.beginStep = begin;
        this.endStep = end;
        this.requiredItems = requiredItems;
        this.poster = poster;
    }

    public boolean isActive(){
        return status == JobStatus.ACTIVE;
    }

    public Storage getStorage(){
        return storage;
    }

    /**
     * Delivers items towards the job, but not more than necessary.
     * @param item an item type
     * @param amount the amount to deliver
     * @param team the team to deliver for
     * @return how many items were actually delivered
     */
    public int deliver(Item item, int amount, String team) {
        if(!(status == JobStatus.ACTIVE)) return 0;
        ItemBox box = getDelivered(team);
        int missing = requiredItems.getItemCount(item) - box.getItemCount(item);
        int store = Math.min(missing, amount);
        box.store(item, store);
        return store;
    }

    /**
     * Gets the box containing all items delivered by the given team towards this job.
     * @param team name of the team
     * @return the box
     */
    private ItemBox getDelivered(String team){
        deliveredItems.putIfAbsent(team, new ItemBox());
        return deliveredItems.get(team);
    }

    /**
     * @return how much money the job awards
     */
    public int getReward(){
        return reward;
    }

    /**
     * Checks if the job has been completed.
     * Sets status accordingly.
     * Moves reward items and partial deliveries to the "delivered" storage.
     * @param team the team to check completion for
     * @return whether the job was completed by that team (and has been active up to now)
     */
    public boolean checkCompletion(String team) {
        if(status == JobStatus.ACTIVE) {
            ItemBox delivered = getDelivered(team);
            boolean completed = requiredItems.isSubset(delivered);
            if(completed){
                status = JobStatus.COMPLETED;
                // transfer partially delivered items
                returnPartialDeliveries(new HashSet<>(Collections.singletonList(team)));
                // transfer required items to posting team
                if(!getPoster().equals(JobData.POSTER_SYSTEM)){
                    storage.addDelivered(requiredItems, getPoster());
                }

                Log.log(Log.Level.NORMAL, "Job completed by " + team + ": " + this);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns partially delivered items to the teams.
     * Should be called if a job is completed or terminated.
     * @param excludeTeams teams which shall not gain items back (e.g. the team that completed the job).
     */
    private void returnPartialDeliveries(Set<String> excludeTeams){
        deliveredItems.entrySet().stream()
                .filter(entry -> !excludeTeams.contains(entry.getKey())) // filter unwanted teams
                .forEach(entry -> storage.addDelivered(entry.getValue(), entry.getKey()));
    }

    /**
     * @return the name of this job
     */
    public String getName(){
        return name;
    }

    /**
     * Gives the job a name if it has none.
     */
    void acquireName(){
        if(name.equals("")) name = "job" + counter.getAndIncrement();
    }

    public int getBeginStep() {
        return beginStep;
    }

    public int getEndStep() {
        return endStep;
    }

    /**
     * A regular job is set to {@link JobStatus#ACTIVE}.
     * Needs to be called before the job's begin step.
     */
    public void activate() {
        status = JobStatus.ACTIVE;
    }

    /**
     * Sets this job's status to ended if it's still not completed.
     */
    public void terminate(){
        if(!(status == JobStatus.COMPLETED)){
           status = JobStatus.ENDED;
           returnPartialDeliveries(new HashSet<>());
        }
    }

    /**
     * @return the current status of the job
     */
    public JobStatus getStatus() {
        return status;
    }

    /**
     * @param withDelivered whether info on which items where delivered by which team should be included
     *                      (i.e whether {@link JobData#deliveredItems} should be filled)
     * @param withPoster whether poster info should be included
     * @return a data object of this job for serialization
     */
    public JobData toJobData(boolean withDelivered, boolean withPoster){
        return new JobData(name, storage.getName(), beginStep, endStep, reward,
                requiredItems.toItemAmountData(),
                withDelivered? getDeliveredData() : null,
                withPoster? getPoster() : null
        );
    }

    /**
     * @return who created/posted this job ({@link JobData#POSTER_SYSTEM or any team name})
     */
    public String getPoster(){
        return poster;
    }

    /**
     * @return a box containing all items required to complete this job. this box should not be modified!
     */
    public ItemBox getRequiredItems(){
        return requiredItems;
    }

    /**
     * @return a snapshot of the currently delivered items
     */
    List<JobData.CompletionData> getDeliveredData(){
        return deliveredItems.entrySet().stream()
                .map(e -> new JobData.CompletionData(
                        e.getKey(),
                        e.getValue().getStoredTypes().stream()
                                .map(item -> new ItemAmountData(item.getName(), e.getValue().getItemCount(item)))
                                .filter(iad -> iad.getAmount() != 0)
                                .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    /**
     * The possible states of a job.<br>
     * {@link #ACTIVE}: job has been started and is not cancelled/completed <br>
     * {@link #AUCTION}: job is currently up for auction <br>
     * {@link #ENDED}: job has ended without completion <br>
     * {@link #COMPLETED}: job has been completed by a team <br>
     * {@link #FUTURE}: job has been created and not activated yet.<br>
     */
    public enum JobStatus{
        ACTIVE, ENDED, COMPLETED, AUCTION, FUTURE
    }

    public String toString(){
        return String.format("type(%s) reward(%d) from(%s)", getClass().getSimpleName(), reward, poster);
    }

    public void delayEndStep(int delay) {
        endStep += delay;
    }
}
