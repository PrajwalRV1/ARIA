import { ComponentFixture, TestBed } from '@angular/core/testing';

import { QuestionBankDashboardComponent } from './question-bank-dashboard.component';

describe('QuestionBankDashboardComponent', () => {
  let component: QuestionBankDashboardComponent;
  let fixture: ComponentFixture<QuestionBankDashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuestionBankDashboardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(QuestionBankDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
