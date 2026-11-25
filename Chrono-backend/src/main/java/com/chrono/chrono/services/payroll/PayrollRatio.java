package com.chrono.chrono.services.payroll;

import java.math.*;

/** Exact rational arithmetic until the statutory whole-franc tariff lookup. */
final class PayrollRatio {
    static final PayrollRatio ZERO=of(BigDecimal.ZERO);
    private final BigInteger numerator,denominator;
    private PayrollRatio(BigInteger n,BigInteger d) {
        if(d.signum()==0)throw new ArithmeticException("Zero denominator");
        if(d.signum()<0){n=n.negate();d=d.negate();} var gcd=n.gcd(d); numerator=n.divide(gcd);denominator=d.divide(gcd);
    }
    static PayrollRatio of(BigDecimal value) {
        return value.scale()<0?new PayrollRatio(value.unscaledValue().multiply(BigInteger.TEN.pow(-value.scale())),BigInteger.ONE)
                :new PayrollRatio(value.unscaledValue(),BigInteger.TEN.pow(value.scale()));
    }
    PayrollRatio add(PayrollRatio v){return new PayrollRatio(numerator.multiply(v.denominator).add(v.numerator.multiply(denominator)),denominator.multiply(v.denominator));}
    PayrollRatio subtract(PayrollRatio v){return add(v.multiply(BigDecimal.valueOf(-1)));}
    PayrollRatio multiply(BigDecimal v){var r=of(v);return new PayrollRatio(numerator.multiply(r.numerator),denominator.multiply(r.denominator));}
    PayrollRatio divide(BigDecimal v){var r=of(v);return new PayrollRatio(numerator.multiply(r.denominator),denominator.multiply(r.numerator));}
    PayrollRatio nonnegative(){return numerator.signum()<0?ZERO:this;}
    BigDecimal rateIncome(){
        if(numerator.signum()<0)throw new IllegalStateException("Negative rate income");
        BigDecimal whole=new BigDecimal(numerator.divide(denominator));
        BigDecimal decimal=new BigDecimal(numerator).divide(new BigDecimal(denominator),12,RoundingMode.HALF_UP);
        // Keep useful decimal evidence, while ensuring the subsequent whole-franc lookup equals
        // the exact rational result even immediately below an integer tariff boundary.
        return decimal.setScale(0,RoundingMode.DOWN).compareTo(whole)==0?decimal:whole.add(new BigDecimal("0.999999999999"));
    }
}
