package scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class JobScheduler {
    // Functional Requirement
    // - Jobs are submitted in real time by users with specific requirements
    // - Each job has a timeout

    // Cluster Resource Management
    // - The system should allow users to add clusters
    // - Each cluster reports its available resources (CPU and RAM) to the scheduler in real time.
    // - The scheduler must allocate jobs efficiently to clusters without overloading them.

    // Real-Time Scheduling
    // - The system continuously monitors job submissions and allocates them to available clusters in real time
    // - Jobs are scheduled on the cluster that can fulfil their requirements
    // - Multiple jobs can run on a cluster simultaneously, provided there are enough resources to meet all job requirements.

    // Job Execution:
    // - Once a job is scheduled, it begins execution immediately on the assigned cluster.
    // - Upon completion, the job releases its allocated resources back to the cluster pool, making them available for new jobs.


    // job -> jobid, required cpu, required memeory, execution time
    // cluster manager -> List<Cluster>
    // cluster -> id , cpu, ram, availablecpu, availableram, lock
    // Job Scheduler -> Cluster Manager, JobQueue , execution  Service

    static class JobResource {
        int cpu;
        int memory;

        public JobResource(int cpu, int memory) {
            this.cpu = cpu;
            this.memory = memory;
        }
    }

    static class Job {
        String jobid;
        JobResource resource;
        int executionTimeInSeconds;

        public Job(String id, int cpu, int memory, int executionTimeInSeconds) {
            this.jobid = id;
            this.resource = new JobResource(cpu, memory);
            this.executionTimeInSeconds = executionTimeInSeconds;
        }
    }

    static class Cluster {
        String id;
        int totalCpu;
        int totalMemory;
        int availableCpu;
        int availableMemory;
        Lock resourceLock;

        public Cluster(String id, int cpu, int memory) {
            this.id = id;
            this.totalCpu = cpu;
            this.totalMemory = memory;
            this.availableCpu = cpu;
            this.availableMemory = memory;
            this.resourceLock = new ReentrantLock();
        }

        public boolean isResourceAvailable(JobResource job) {
            return (job.cpu <= availableCpu) && (job.memory <= availableMemory);
        }

        public boolean allocateResource(JobResource job) {
            try{
                System.out.println("Trying to allocate Cluster "+ this.id + " at " + System.currentTimeMillis());
                if(resourceLock.tryLock(5, TimeUnit.MILLISECONDS)) {
                    try {
                        if(isResourceAvailable(job)) {
                            this.availableCpu -= job.cpu;
                            this.availableMemory -= job.memory;
                            return true;
                        } else {
                            System.out.println("Resource not available for cluster " + id + " at " + System.currentTimeMillis());
                            return false;
                        }
                    } finally {
                        System.out.println("Releasing lock for cluster " + id + " at " + System.currentTimeMillis());
                        resourceLock.unlock();
                    }
                } else {
                    return false;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }

        public void deallocatedResource(JobResource job) {
            resourceLock.lock();
            try{
                availableMemory += job.memory;
                availableCpu += job.cpu;
            } finally {
                 resourceLock.unlock();
            }
        }
    }

    static class ClusterManager {
        static ClusterManager instance;
        List<Cluster> clusterList;

        private ClusterManager() {
            this.clusterList = new ArrayList<>();
        }

        public static synchronized ClusterManager getInstace() {
            if(instance == null) {
                instance = new ClusterManager();
            }
            return instance;
        }

        public void addCluster(Cluster cluster) {
            this.clusterList.add(cluster);
        }

        public Cluster getAvailableCluster(JobResource job) {
            for(Cluster cluster : clusterList) {
                if(cluster.allocateResource(job)) {
                    return  cluster;
                }
            }

            return null;
        }
    }

    static class Scheduler {
        ClusterManager clusterManager;
        BlockingQueue<Job> jobQueue;
        ExecutorService executorService;

        public Scheduler() {
            this.clusterManager = ClusterManager.getInstace();
            // Adding clusters - seed data
            clusterManager.addCluster(new Cluster("A", 8, 32)); // 8 CPU, 32 GB RAM
            clusterManager.addCluster(new Cluster("B", 16, 64)); // 16 CPU, 64 GB RAM

            this.jobQueue = new ArrayBlockingQueue<>(100);
            this.executorService = Executors.newFixedThreadPool(5);

            for(int i = 0; i< 5; i++){
                executorService.submit(this::executeJob);
            }
        }

        public void submitJob(Job job) {
            System.out.println("Submitting job "+job.jobid + " at "+System.currentTimeMillis());
            jobQueue.add(job);
            System.out.println("Job "+job.jobid+" added to the queue at "+System.currentTimeMillis());
        }

        private void executeJob() {
            while (true) {

                System.out.println("id : " + Thread.currentThread().getId());
                try{
                    Job job = jobQueue.take();
                    Cluster cluster = clusterManager.getAvailableCluster(job.resource);
                    if(cluster == null) {
                        System.out.println("No Cluster is available for job id " + job.jobid + " at " + System.currentTimeMillis());
                        jobQueue.offer(job);
                        continue;
                    }
                    startJob(cluster, job);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void startJob(Cluster cluster, Job job) {
            long currTime = System.currentTimeMillis();
            System.out.println("Job " + job.jobid + " started on cluster "+cluster.id + " at " + currTime);
            try {
                Thread.sleep(job.executionTimeInSeconds * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                cluster.deallocatedResource(job.resource);
            }
        }
    }

    public static void main(String[] args) {
        Scheduler scheduler = new Scheduler();

        // Creating jobs
        Job job1 = new Job("1", 4, 16, 3); // 4 CPU, 16 GB RAM, 60s
        Job job2 = new Job("2", 2, 8, 9);  // 2 CPU, 8 GB RAM, 90s
        Job job3 = new Job("3", 6, 24, 12); // 6 CPU, 24 GB RAM, 120s

        // Submit jobs
        scheduler.submitJob(job1);
        scheduler.submitJob(job2);
        scheduler.submitJob(job3);

    }
}
