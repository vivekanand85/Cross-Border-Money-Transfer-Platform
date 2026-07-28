package com.moneytransfer.orchestration_service.statemachine;

import java.util.*;

public enum TransferState {
	INITIATED{
		@Override
		public Set<TransferState> allowedNextStates(){
			return EnumSet.of(SCREENING,FAILED);
		}
	},
	
	SCREENING{
		public Set<TransferState> allowedNextStates(){
			return EnumSet.of(PENDING_REVIEW,PAY_IN,FAILED);
		}
	},
	PENDING_REVIEW{
		@Override
		public Set<TransferState> allowedNextStates(){
			return EnumSet.of(PAY_IN,FAILED);
		}
	},
	PAY_IN {
        @Override
        public Set<TransferState> allowedNextStates() {
            return EnumSet.of(PAY_OUT, FAILED, REVERSED);
        }
    },
	PAY_OUT {
        @Override
        public Set<TransferState> allowedNextStates() {
            return EnumSet.of(SETTLED, FAILED, REVERSED,AWAITING_PICKUP);
        }
    },
	AWAITING_PICKUP{
    	@Override
    	public Set<TransferState> allowedNextStates(){
    		return EnumSet.of(SETTLED,FAILED,REVERSED);
    	}
    },
	SETTLED {
        @Override
        public Set<TransferState> allowedNextStates() {
            return EnumSet.noneOf(TransferState.class); // terminal
        }
    },
	FAILED {
        @Override
        public Set<TransferState> allowedNextStates() {
            return EnumSet.noneOf(TransferState.class); // terminal
        }
    },
	 REVERSED {
        @Override
        public Set<TransferState> allowedNextStates() {
            return EnumSet.noneOf(TransferState.class); // terminal
        }
    };
	public abstract Set<TransferState> allowedNextStates();
	
	public boolean canTransitionTo(TransferState target) {
		return allowedNextStates().contains(target);
	}
}
