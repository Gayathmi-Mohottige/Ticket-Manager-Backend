package com.ticketManager.ticketManager.service;

import com.ticketManager.ticketManager.model.TicketPool;
import com.ticketManager.ticketManager.repository.TicketPoolRepository;
import com.ticketManager.ticketManager.model.Transaction;
import com.ticketManager.ticketManager.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;

@Service
public class TicketPoolService {
    private final ConcurrentLinkedQueue<Integer> ticketPool = new ConcurrentLinkedQueue<>();
    private int ticketsProduced = 0;
    private boolean noMoreTickets = false;
    private int totalTickets;
    private int maxTicketCapacity;

    @Autowired
    private TicketPoolRepository ticketPoolRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public synchronized int initializeTicketPool(int totalTickets, int maxTicketCapacity) {
        this.totalTickets = totalTickets;
        this.maxTicketCapacity = maxTicketCapacity;
        this.ticketPool.clear();
        this.ticketsProduced = 0;
        this.noMoreTickets = false;

        TicketPool pool = new TicketPool(totalTickets);
        ticketPoolRepository.save(pool);
        return pool.getId();
    }

    private void updateTicketPoolSize(int poolID) {
        TicketPool pool = ticketPoolRepository.findById(poolID).orElseThrow();  // Assuming one pool record
        pool.setCurrentSize(ticketPool.size());
        ticketPoolRepository.save(pool);
    }

    public List<Transaction> getTransactionHistory() {
        return transactionRepository.findAll();
    }

    public synchronized boolean addTicket(String vendorName, int ticketsToAdd , int poolId) {
        if(noMoreTickets || ticketsProduced >= maxTicketCapacity) {
            noMoreTickets = true;
            return false;
        }

        int ticketsToAddToThisCycle = Math.min(ticketsToAdd, maxTicketCapacity - ticketsProduced );

        while (ticketPool.size() + ticketsToAddToThisCycle > totalTickets) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        for (int i = 0; i < ticketsToAddToThisCycle; i++) {
            ticketPool.add(1);
        }
        ticketsProduced += ticketsToAddToThisCycle;

        Transaction transaction = new Transaction("ADD", vendorName, ticketsToAddToThisCycle, ticketPool.size());
        transactionRepository.save(transaction);

        updateTicketPoolSize(poolId);
        notifyAll();
        return true;

    }

    public synchronized void removeTickets (String customerName, int ticketsToBuy, int poolId) {
        while(ticketPool.size() < ticketsToBuy) {
            if (noMoreTickets && !ticketPool.isEmpty()) {
                return;
            }
            try {
                wait();
            }catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        for (int i = 0; i < ticketsToBuy; i++) {
            ticketPool.poll();
        }

        Transaction transaction = new Transaction("RETRIEVE", customerName, ticketsToBuy, ticketPool.size());
        transactionRepository.save(transaction);

        updateTicketPoolSize(poolId);
        notifyAll();
    }

    public int getTicketPoolSize() {
        return ticketPool.size();
    }

    public boolean isNoMoreTickets() {
        return noMoreTickets;
    }

    public void stopSimulation() {
        noMoreTickets = true;
    }
}
