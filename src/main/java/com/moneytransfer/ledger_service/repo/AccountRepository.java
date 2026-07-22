package com.moneytransfer.ledger_service.repo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.moneytransfer.ledger_service.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID>{

}
