package problems;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import core.AbstractBinaryProblem;
import core.BinarySolution;
import core.JMetalRandom;


/**
 * Knapsack01
 * @author Zimin Liang <zimin.liang@outlook.com>
 * An implementation of bi-objective MOKP.
 * The optima for each objective is pre-computed with dynamic programming.
 * Therefore, for each objective, the objective computed by the distance 
 * to that single-objective optima.
 * This goes under the minisation framework of jMetal.
 */
public class MOKP extends AbstractBinaryProblem {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7200841792019107271L;
	int n;
	public double weightLimit;
	double[] opt1, opt2;
	public double[] weight;
	public double[] value1;
	public double[] value2;
	private List<Integer> bitsPerVariable;
	double[][] refFront = null;
	
	public MOKP() {
		initProblem(50);
	}

	public MOKP(int n){
		initProblem(n);
	}
	
	public double[] getOpt() {
		double[] res = new double[2];
		res[0] = opt1[0]; res[1] = opt2[1];
		return res;
	}
	
	@Override
	public void setName(String name) {
		super.setName(name);
	}
	
	public MOKP load(String path) {
		FileInputStream inputStream;
		try {
			inputStream = new FileInputStream(path);
			DataInputStream reader = new DataInputStream(inputStream);
			this.n = Integer.valueOf(reader.readLine());
			setName("KP-"+n);
			this.weightLimit = Double.valueOf(reader.readLine());
			for (int i=0; i<this.n; i++) {
				String[] str = reader.readLine().split(" ");
				weight[i] = Double.valueOf(str[0]);
				value1[i] = Double.valueOf(str[1]);
				value2[i] = Double.valueOf(str[2]);
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			save(path);
		}
	    //opt1 = optimalValue(0);
	    //opt2 = optimalValue(1);
	    System.out.println(path);
	    return this;
	}
	
	public MOKP loadXiaofeng(String path) {
        int m = getNumberOfObjectives();
        int n = getNumberOfVariables();
        String wfile = path + "MOKP-M" + m + "-D" + n + "_W.txt";
        String pfile = path + "MOKP-M" + m + "-D" + n + "_P.txt";

        // Read weights
        double totalWeight = 0;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(wfile))) {
            String[] w = reader.readLine().trim().split("\\s+");  // splits on one or more spaces
            for (int i = 0; i < n; i++) {
                weight[i] = Double.parseDouble(w[i + 1]);
                totalWeight += weight[i];
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        weightLimit = totalWeight / 2;

        // Read prices
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(pfile))) {
            String[] p1 = reader.readLine().trim().split("\\s+");
            String[] p2 = reader.readLine().trim().split("\\s+");
            for (int i = 0; i < n; i++) {
                value1[i] = Double.parseDouble(p1[i + 1]);
                value2[i] = Double.parseDouble(p2[i + 1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Compute optimal values
        opt1 = optimalValue(0);
        opt2 = optimalValue(1);

        return this;
	}
	

	
	public void save(String path) {
		File file = new File(path);
		if (file.exists()) {
			System.out.println("Save failed, file exists");
			return;
		}
		try {
			PrintWriter writer = new PrintWriter(file);
			writer.println(n);
			writer.println(weightLimit);
			for(int i=0; i<n; i++) {
				writer.print(weight[i]);
				writer.print(' ');
				writer.print(value1[i]);
				writer.print(' ');
				writer.print(value2[i]);
				writer.println();
			}
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	
	public static class DPEntry {
		public int n;
		public int m;
		public double f2Req;
		public DPEntry(int n, int m, double f2Req) {
			this.n=n; this.m=m; this.f2Req=f2Req;
		}
		@Override
		public boolean equals(Object that) {
			DPEntry t = (DPEntry) that;
			return this.n==t.n && this.m==t.m && this.f2Req==t.f2Req;
		}
		@Override
		public int hashCode() {
			return ((int) (f2Req+m))*10000+n;
		}
	}
	
	double[] doubles(double... x) {return x;}
	
	public void initProblem(int n) {
		this.n = n;
		setNumberOfVariables(n);
		setNumberOfObjectives(2);
		setNumberOfConstraints(1);
	    setName("KP-"+n);
	    bitsPerVariable = new ArrayList<>(n);
	    for (int i=0; i<n; i++) bitsPerVariable.add(1);
	    
	    JMetalRandom random = JMetalRandom.getInstance();
	    weight = new double[n];
	    value1 = new double[n];
	    value2 = new double[n];
	    for (int i=0; i<n; i++) {
	    	weight[i] = random.nextInt(10, 100);
	    	value1[i] = random.nextInt(10, 100);
	    	value2[i] = random.nextInt(10, 100);
	    } 
	    
	    // calculate weight limit
	    double totalWeight = 0;
	    for (int i=0; i<n; i++) totalWeight += weight[i];
	    weightLimit = totalWeight / 2;
	    //opt1 = optimalValue(0);
	    //opt2 = optimalValue(1);
	}
	
	public double[] optimalValue(int optChoice) {
		int m = (int)weightLimit;
		double[][][] dptable = new double[2][m+1][2];
		for (int w=0; w<=m; w++) {
			if (weight[0]<=w) {
				dptable[0][w] = doubles(value1[0],value2[0]);
			}
			else dptable[0][w] = doubles(0, 0);
		}
		
		for (int i=1; i<n; i++) {
			for (int w=0; w<=m; w++) {
				double[] notGet = dptable[0][w];
				double[] get = weight[i]<=w 
						? doubles(
								dptable[0][(int) (w-weight[i])][0] + value1[i],
								dptable[0][(int) (w-weight[i])][1] + value2[i])
						: doubles(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY);
				dptable[1][w] = notGet[optChoice]>=get[optChoice]? notGet : get;
			}
			for (int w=0; w<=m; w++) dptable[0][w] = dptable[1][w];
		}

		return dptable[1][m];
	}
	

	@Override
	public synchronized BinarySolution evaluate(BinarySolution solution) {
		double totalWeight = 0;
		double totalValue1 = 0;
		double totalValue2 = 0;
		for (int i=0; i<n; i++) {
			boolean bit = solution.variables().get(i).get(0);
			if (bit) {
				totalWeight += weight[i];
				totalValue1 += value1[i];
				totalValue2 += value2[i];
			}
		}
		
		if (totalWeight<=weightLimit) {
			solution.objectives()[0] = - totalValue1;
			solution.objectives()[1] = - totalValue2;
			solution.constraints()[0] = 0;
		} else {
			double violation = (1/n) * (totalWeight - weightLimit);
			solution.objectives()[0] = Double.MAX_VALUE/2+violation;
			solution.objectives()[1] = Double.MAX_VALUE/2+violation;
			solution.constraints()[0] = -(totalWeight - weightLimit);
			
		}
		return solution;
	}

	@Override
	public List<Integer> getListOfBitsPerVariable() {
		return bitsPerVariable;
	}
	
	public double[] getWeight() {
		return weight;
	}
	
	public static void main(String[] args) {
		int[] ns = {100, 500, 1000, 5000};
		for (int n : ns) {
			var problem = new MOKP(n);
			problem.save("D:/GECCO26scale/Data/"+problem.getName()+".txt");
		}
	}


}
