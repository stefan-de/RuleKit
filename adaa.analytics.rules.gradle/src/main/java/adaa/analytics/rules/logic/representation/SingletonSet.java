package adaa.analytics.rules.logic.representation;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

public class SingletonSet implements IValueSet, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1567922506451323157L;
	protected double value;
	protected List<String> mapping;
	
	public double getValue() { return value; }
	public void setValue(double v) { value = v; }
	
	public List<String> getMapping() { return mapping; }
	public void setMapping(List<String> v) { mapping = v; }
	
	public SingletonSet(double v, List<String> mapping) {
		this.value = v;
		this.mapping = mapping;
	}
	
	@Override
	public boolean contains(double value) {
		return (value == this.value) || (Double.isNaN(value) && MissingValuesHandler.ignore);
	}

	@Override
	public boolean intersects(IValueSet set) {
		SingletonSet ss = (set instanceof SingletonSet) ? (SingletonSet)set : null;
		if (ss != null) {
			return this.value == ss.value;
		}
		return false;
	}
	
	@Override
	public boolean equals(Object obj) {
		SingletonSet ref = (obj instanceof SingletonSet) ? (SingletonSet)obj : null;
		
		if (ref != null) {
			EqualsBuilder builder = new EqualsBuilder();
			builder.append(value,  ref.value);
			builder.append(mapping, ref.mapping);
			return builder.isEquals();
		} else {
			return false;
		}
	}

	@Override
	public String toString() {
		String s = "{" + ((mapping == null) ? value : mapping.get((int)value)) + "}";
		return s;
	}
	
	@Override
	public IValueSet getIntersection(IValueSet set) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public int hashCode() {
		HashCodeBuilder builder = new HashCodeBuilder(19,27);
		builder.append(value).append(mapping);
		return builder.toHashCode();
	}
	@Override
	public List<IValueSet> getDifference(IValueSet set) {
		// TODO Auto-generated method stub
		return null;
	}
}
