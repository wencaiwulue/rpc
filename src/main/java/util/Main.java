package util;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

class Buffer {
  private final ReentrantLock lock;
  private final Condition notFull;
  private final Condition notEmpty;
  private final int maxSize;
  private final List<Date> storage;
  //  ArrayBlockingQueue queue = new ArrayBlockingQueue(100);

  Buffer(int size) {
    lock = new ReentrantLock();
    notFull = lock.newCondition();
    notEmpty = lock.newCondition();
    maxSize = size;
    storage = new LinkedList<>();
  }

  public void put() {
    try {
      lock.lock();
      while (storage.size() == maxSize) { // 如果队列满了
        System.out.print(Thread.currentThread().getName() + ": wait \n");
        notFull.await(); // 阻塞生产线程
      }
      storage.add(new Date());
      System.out.print(Thread.currentThread().getName() + ": put:" + storage.size() + "\n");
      notEmpty.signal();
      Thread.sleep(1);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      lock.unlock();
    }
  }

  public void take() {
    try {
      lock.lock();
      while (storage.size() == 0) { // 如果队列不空
        notEmpty.await(); // 阻塞消费线程
      }
      Date d = ((LinkedList<Date>) storage).poll();
      System.out.print(Thread.currentThread().getName() + ": take:" + storage.size() + "\n");
      notFull.signal();
      Thread.sleep(1000);
      System.out.print(Thread.currentThread().getName() + ": wait \n");
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      lock.unlock();
    }
  }
}

class Producer implements Runnable {
  private Buffer buffer;

  Producer(Buffer b) {
    buffer = b;
  }

  @Override
  public void run() {
    while (true) {
      buffer.put();
    }
  }
}

class Consumer implements Runnable {
  private Buffer buffer;

  Consumer(Buffer b) {
    buffer = b;
  }

  @Override
  public void run() {
    while (true) {
      buffer.take();
    }
  }
}

public class Main {
  public static void main(String[] arg) {
    Buffer buffer = new Buffer(10);
    Producer producer = new Producer(buffer);
    Consumer consumer = new Consumer(buffer);
    for (int i = 0; i < 2; i++) {
      new Thread(producer, "producer-" + i).start();
    }
    for (int i = 0; i < 4; i++) {
      new Thread(consumer, "consumer-" + i).start();
    }
  }
}
